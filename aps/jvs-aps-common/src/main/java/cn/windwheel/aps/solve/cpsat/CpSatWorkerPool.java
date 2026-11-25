package cn.windwheel.aps.solve.cpsat;

import cn.windwheel.aps.cpsat.Protocol;
import cn.windwheel.aps.cpsat.Protocol.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Exclusive worker leases, tenant-affine cache, bounded lifetime and fail-closed cancellation. */
public final class CpSatWorkerPool implements AutoCloseable {
    public record Result(Response response,long pid,boolean reused,int completedJobs,long processStartMillis,Long cpuMillis) {}
    private static final long IDLE_NANOS=TimeUnit.SECONDS.toNanos(60);
    private final Deque<Worker> idle=new ArrayDeque<>();
    private final Set<Worker> all=new HashSet<>();
    private final ScheduledExecutorService reaper=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"cp-sat-worker-reaper");t.setDaemon(true);return t;});
    private boolean closed;
    public CpSatWorkerPool() {reaper.scheduleWithFixedDelay(this::reap,10,10,TimeUnit.SECONDS);}
    private static final class Worker {
        Path dir;Process process;BufferedWriter commands;String key;int jobs;long idleSince;boolean busy;
    }
    public Result execute(Path jar,String tenant,Request request,byte[] bytes,boolean reuse,long deadline,BooleanSupplier cancelled) throws Exception {
        Worker w=borrow(jar,tenant,reuse);boolean healthy=false;
        Path input=w.dir.resolve(request.attempt()+".request.json"),output=w.dir.resolve(request.attempt()+".response.json");
        try {
            boolean reused=w.jobs>0;long start=System.nanoTime();
            if(w.process==null) {
                w.process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx512m","-jar",jar.toString(),
                        "--daemon",w.dir.toString(),Long.toString(ProcessHandle.current().pid()))
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                w.commands=new BufferedWriter(new OutputStreamWriter(w.process.getOutputStream(),java.nio.charset.StandardCharsets.UTF_8));
            }
            long launchMillis=(System.nanoTime()-start)/1000000;
            var cpuBefore=w.process.info().totalCpuDuration();
            Files.write(input,bytes,StandardOpenOption.CREATE_NEW);
            w.commands.write(request.attempt());w.commands.newLine();w.commands.flush();
            while(!Files.exists(output)) {
                if(cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
                if(System.nanoTime()>deadline) throw new TimeoutException("CP-SAT超出总预算，worker已终止");
                if(!w.process.isAlive()) throw new IllegalStateException("CP-SAT worker异常退出，退出码="+w.process.exitValue());
                Thread.sleep(50);
            }
            if(cancelled.getAsBoolean()) throw new CancellationException();
            if(Files.size(output)>16L*1024*1024) throw new IllegalStateException("CP-SAT响应超出限制");
            Response response=new ObjectMapper().readValue(output.toFile(),Response.class);
            if(response.version()!=Protocol.VERSION || !request.attempt().equals(response.attempt())) throw new IllegalStateException("CP-SAT响应身份不匹配");
            w.jobs++;healthy=!"MODEL_OR_RUNTIME_ERROR".equals(response.status());
            var cpuAfter=w.process.info().totalCpuDuration();
            Long cpuMillis=cpuBefore.isPresent() && cpuAfter.isPresent()?cpuAfter.get().minus(cpuBefore.get()).toMillis():null;
            return new Result(response,w.process.pid(),reused,w.jobs,launchMillis,cpuMillis);
        } finally {
            try {Files.deleteIfExists(input);Files.deleteIfExists(output);Files.deleteIfExists(output.resolveSibling(output.getFileName()+".tmp"));}
            finally {release(w,healthy && reuse);}
        }
    }
    private synchronized Worker borrow(Path jar,String tenant,boolean reuse) throws IOException {
        if(closed) throw new IllegalStateException("CP-SAT worker pool closed");reap();
        String key=tenant+"\n"+jar.toRealPath()+"\n"+Files.size(jar)+"\n"+Files.getLastModifiedTime(jar);
        if(reuse) for(var it=idle.iterator();it.hasNext();) {
            Worker w=it.next();if(w.key.equals(key)) {it.remove();w.busy=true;return w;}
        }
        // Never accumulate idle processes across tenants or jar revisions.
        while(all.size()>=2 && !idle.isEmpty()) destroy(idle.removeFirst());
        if(all.size()>=2) throw new IllegalStateException("CP-SAT worker池已满");
        Worker w=new Worker();w.key=key;w.busy=true;w.dir=Files.createTempDirectory("aps-cpsat-pool-");
        if(Files.getFileStore(w.dir).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(w.dir,PosixFilePermissions.fromString("rwx------"));
        all.add(w);return w;
    }
    private synchronized void release(Worker w,boolean healthy) {
        w.busy=false;
        if(closed || !healthy || w.jobs>=20 || w.process==null || !w.process.isAlive()) destroy(w);
        else {w.idleSince=System.nanoTime();idle.addLast(w);}
    }
    private synchronized void reap() {
        for(var w:new ArrayList<>(idle)) if(w.process==null || !w.process.isAlive() || System.nanoTime()-w.idleSince>IDLE_NANOS) destroy(w);
    }
    private void destroy(Worker w) {
        idle.remove(w);all.remove(w);
        if(w.process!=null && w.process.isAlive()) {
            w.process.destroy();
            try {if(!w.process.waitFor(500,TimeUnit.MILLISECONDS)) {w.process.destroyForcibly();w.process.waitFor(2,TimeUnit.SECONDS);}}
            catch(InterruptedException ex) {w.process.destroyForcibly();Thread.currentThread().interrupt();}
        }
        if(w.commands!=null) try {w.commands.close();} catch(IOException ignored) {}
        try(var paths=Files.walk(w.dir)) {for(var p:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);}
        catch(IOException ignored) { /* Host temp janitor can retry after a delayed native shutdown. */ }
    }
    @Override public synchronized void close() {closed=true;reaper.shutdownNow();for(var w:new ArrayList<>(all))destroy(w);}
}
