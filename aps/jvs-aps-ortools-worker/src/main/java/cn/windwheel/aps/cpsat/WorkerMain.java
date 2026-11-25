package cn.windwheel.aps.cpsat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicLong;
import static cn.windwheel.aps.cpsat.Protocol.*;

/** Bounded daemon or one-shot worker. Cancellation destroys the entire native process. */
public final class WorkerMain {
    public static void main(String[] args) throws Exception {
        if(args.length==3 && "--daemon".equals(args[0])) {
            Path dir=Path.of(args[1]).toRealPath();long parent=Long.parseLong(args[2]);
            AtomicLong deadline=new AtomicLong(System.nanoTime()+120_000_000_000L);
            watchdog(parent,deadline);
            WorkerModelCache cache=new WorkerModelCache();
            try(var reader=new BufferedReader(new InputStreamReader(System.in,java.nio.charset.StandardCharsets.UTF_8))) {
                String id;int jobs=0;
                while(jobs++<20 && (id=reader.readLine())!=null) {
                    if(!id.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Invalid job id");
                    Path in=dir.resolve(id+".request.json"),out=dir.resolve(id+".response.json");
                    Request request=read(in);
                    if(!id.equals(request.attempt()) || parent!=request.parentPid()) throw new IllegalArgumentException("Request identity mismatch");
                    deadline.set(System.nanoTime()+(long)((request.seconds()+10)*1e9));
                    execute(request,out,cache);
                    deadline.set(System.nanoTime()+120_000_000_000L);
                }
            }
        } else if(args.length==2) {
            Request request=read(Path.of(args[0]));
            watchdog(request.parentPid(),new AtomicLong(System.nanoTime()+(long)((request.seconds()+10)*1e9)));
            execute(request,Path.of(args[1]),new WorkerModelCache());
        } else throw new IllegalArgumentException("Usage: worker request response | --daemon directory parentPid");
    }
    private static Request read(Path input) throws Exception {
        if(Files.size(input)>32L*1024*1024) throw new IllegalArgumentException("Request too large");
        return new ObjectMapper().readValue(input.toFile(),Request.class);
    }
    private static void execute(Request request,Path output,WorkerModelCache cache) throws Exception {
        Response response;
        try {response=cache.solve(request);}
        catch(Exception|LinkageError failure) {
            String message=failure.getClass().getSimpleName()+": "+String.valueOf(failure.getMessage());
            response=new Response(VERSION,request.attempt(),"MODEL_OR_RUNTIME_ERROR",message.substring(0,Math.min(2000,message.length())),
                    java.util.List.of(),null,0,0,0,0,0,false);
        }
        Path temporary=output.resolveSibling(output.getFileName()+".tmp");
        new ObjectMapper().writeValue(temporary.toFile(),response);
        Files.move(temporary,output,StandardCopyOption.ATOMIC_MOVE);
    }
    private static void watchdog(long parent,AtomicLong deadline) {
        Thread watchdog=new Thread(()->{
            try {
                while(System.nanoTime()<deadline.get() && ProcessHandle.of(parent).map(ProcessHandle::isAlive).orElse(false)) Thread.sleep(100);
                Runtime.getRuntime().halt(124);
            } catch(InterruptedException ignored) {Thread.currentThread().interrupt();}
        },"parent-deadline-watchdog");watchdog.setDaemon(true);watchdog.start();
    }
}
