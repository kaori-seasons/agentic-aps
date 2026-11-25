package cn.windwheel.aps.cpsat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.HexFormat;
import static cn.windwheel.aps.cpsat.Protocol.*;

/** One bounded, process-local entry; tenant pools never share processes. No solver/search-state reuse. */
public final class WorkerModelCache {
    private String key;
    private CpSatSolver compiled;
    public Response solve(Request request) throws Exception {
        var semantic=new Request(VERSION,"",0,request.originEpochMillis(),request.horizon(),1,1,0,
                request.weights(),request.resources(),request.tasks(),request.baseline(),request.optimization());
        String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(new ObjectMapper().writeValueAsBytes(semantic)));
        CpSatSolver solver=digest.equals(key)?compiled:new CpSatSolver();
        try {
            Response response=solver.solve(request);
            if(solver.modelBytes()<=16*1024*1024) {key=digest;compiled=solver;}
            else {key=null;compiled=null;}
            return response;
        } catch(Exception|LinkageError error) {key=null;compiled=null;throw error;}
    }
}
