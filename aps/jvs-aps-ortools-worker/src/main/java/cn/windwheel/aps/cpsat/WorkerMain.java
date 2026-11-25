package cn.windwheel.aps.cpsat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import static cn.windwheel.aps.cpsat.Protocol.*;

/** One request per JVM: native failure, cancellation and protobuf stay outside the web service. */
public final class WorkerMain {
    public static void main(String[] args) throws Exception {
        if(args.length!=2) throw new IllegalArgumentException("Usage: worker request.json response.json");
        Path input=Path.of(args[0]),output=Path.of(args[1]);
        if(Files.size(input)>32L*1024*1024) throw new IllegalArgumentException("Request too large");
        ObjectMapper mapper=new ObjectMapper(); Request request=mapper.readValue(input.toFile(),Request.class);
        Thread watchdog=new Thread(()->{
            try {
                long deadline=System.nanoTime()+(long)((request.seconds()+10)*1e9);
                while(System.nanoTime()<deadline && ProcessHandle.of(request.parentPid()).map(ProcessHandle::isAlive).orElse(false))
                    Thread.sleep(200);
                Runtime.getRuntime().halt(124);
            } catch(InterruptedException ignored) { Thread.currentThread().interrupt(); }
        },"parent-deadline-watchdog"); watchdog.setDaemon(true); watchdog.start();
        Response response;
        try { response=new CpSatSolver().solve(request); }
        catch(RuntimeException|LinkageError failure) {
            String message=failure.getClass().getSimpleName()+": "+String.valueOf(failure.getMessage());
            response=new Response(VERSION,request.attempt(),"MODEL_OR_RUNTIME_ERROR",message.substring(0,Math.min(2000,message.length())),
                    java.util.List.of(),null,0,0,0,0,0,false);
        }
        Path temporary=output.resolveSibling(output.getFileName()+".tmp");
        mapper.writeValue(temporary.toFile(),response);
        Files.move(temporary,output,StandardCopyOption.ATOMIC_MOVE);
    }
}
