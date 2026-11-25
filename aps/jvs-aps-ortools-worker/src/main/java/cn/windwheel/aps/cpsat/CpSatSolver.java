package cn.windwheel.aps.cpsat;

import com.google.ortools.Loader;
import com.google.ortools.sat.*;
import java.util.*;
import static cn.windwheel.aps.cpsat.Protocol.*;

/** Optional-machine job shop with exact calendar envelopes and six business objective terms. */
public final class CpSatSolver {
    private final CpModel model=new CpModel();
    private final Map<String,Vars> vars=new LinkedHashMap<>();
    private final LinearExprBuilder objective=LinearExpr.newBuilder();
    private Request request;
    private record Vars(IntVar start,IntVar end,Map<String,BoolVar> presence) {}
    private IntVar integer(long max,String name) { return model.newIntVar(0,max,name); }
    private static LinearExpr plus(LinearArgument a,long c) { return LinearExpr.newBuilder().add(a).add(c).build(); }
    private static LinearExpr minus(LinearArgument a,LinearArgument b) { return LinearExpr.newBuilder().add(a).addTerm(b,-1).build(); }
    private void term(LinearArgument value,long weight) { if(weight!=0) objective.addTerm(value,weight); }
    private IntVar minutes(LinearArgument expression,long upper,String name) {
        IntVar positive=integer(upper,name+"Positive"), minutes=integer(upper/60000,name);
        model.addMaxEquality(positive,new LinearArgument[]{LinearExpr.constant(0),expression});
        model.addDivisionEquality(minutes,positive,LinearExpr.constant(60000));
        return minutes;
    }
    public Response solve(Request r) {
        long begin=System.nanoTime(); request=r;
        if(r.version()!=VERSION || r.tasks().isEmpty() || r.tasks().size()>500 || r.horizon()<=0
                || r.horizon()>180L*86400000 || r.seconds()<0.1 || r.seconds()>3600 || r.workers()<1 || r.workers()>8)
            throw new IllegalArgumentException("Invalid/budget-exceeding CP-SAT request");
        Loader.loadNativeLibraries();
        Map<String,List<IntervalVar>> occupancy=new LinkedHashMap<>();
        for(var resource:r.resources()) {
            var intervals=new ArrayList<IntervalVar>(); occupancy.put(resource.id(),intervals);
            // Merge is performed by the adapter; overlapping downtime intervals must not make the model infeasible.
            for(var w:resource.downtime()) intervals.add(model.newFixedInterval(w.start(),w.end()-w.start(),"downtime"));
        }
        for(var t:r.tasks()) {
            if(vars.containsKey(t.id())) throw new IllegalArgumentException("Duplicate task id");
            IntVar s=model.newIntVar(Math.max(0,t.release()),r.horizon(),t.id()+"/start"), e=integer(r.horizon(),t.id()+"/end");
            var ps=new LinkedHashMap<String,BoolVar>();
            for(var o:t.options()) {
                if(o.processing()<=0 || !occupancy.containsKey(o.resource()) || ps.containsKey(o.resource()))
                    throw new IllegalArgumentException("Invalid machine option");
                BoolVar p=model.newBoolVar(t.id()+"/"+o.resource()); ps.put(o.resource(),p);
                IntVar size=model.newIntVar(1,r.horizon(),"elapsed");
                occupancy.get(o.resource()).add(model.newOptionalIntervalVar(s,size,e,p,t.id()));
                if(t.frozen()!=null) {
                    model.addEquality(p,o.resource().equals(t.frozen().resource())?1:0);
                    model.addEquality(s,t.frozen().start()); model.addEquality(e,t.frozen().end());
                } else {
                    var segments=new ArrayList<BoolVar>();
                    for(var segment:o.segments()) {
                        BoolVar b=model.newBoolVar("calendarSegment"); segments.add(b);
                        model.addGreaterOrEqual(s,segment.firstStart()).onlyEnforceIf(b);
                        model.addLessOrEqual(s,segment.lastStart()).onlyEnforceIf(b);
                        model.addEquality(e,plus(s,segment.elapsed())).onlyEnforceIf(b);
                    }
                    model.addEquality(LinearExpr.sum(segments.toArray(BoolVar[]::new)),p);
                }
            }
            model.addExactlyOne(ps.values().toArray(BoolVar[]::new));
            vars.put(t.id(),new Vars(s,e,ps));
            if(t.due()!=null) term(minutes(plus(e,-t.due()),Math.addExact(r.horizon(),Math.max(0,-t.due())),"tardiness"),r.weights().tardiness());
        }
        occupancy.values().forEach(model::addNoOverlap);
        for(var t:r.tasks()) {
            Vars v=vars.get(t.id()); var ends=new ArrayList<IntVar>();
            for(var d:t.dependencies()) {
                Vars f=vars.get(d.predecessor()); if(f==null) throw new IllegalArgumentException("Missing predecessor");
                model.addGreaterOrEqual(v.start(),plus(d.endEnd()?f.start():f.end(),d.startLag()));
                if(d.endEnd()) model.addGreaterOrEqual(v.end(),plus(f.end(),d.endLag()));
                ends.add(f.end());
            }
            if(!ends.isEmpty() && r.weights().gap()>0) {
                IntVar last=integer(r.horizon(),"lastPredecessor"); model.addMaxEquality(last,ends);
                term(minutes(minus(v.start(),last),r.horizon(),"dependencyGap"),r.weights().gap());
            }
        }
        IntVar maxEnd=integer(r.horizon(),"makespan");
        model.addMaxEquality(maxEnd,vars.values().stream().map(Vars::end).toList());
        IntVar epochSeconds=integer(Math.addExact(r.originEpochMillis(),r.horizon())/1000,"finishEpochSeconds");
        model.addDivisionEquality(epochSeconds,plus(maxEnd,r.originEpochMillis()),LinearExpr.constant(1000));
        term(epochSeconds,r.weights().finish());
        for(var resource:r.resources()) machine(resource);
        Long baselineCost=null;
        if(r.baseline()!=null && !r.baseline().isEmpty()) {
            baselineCost=Protocol.verify(r,r.baseline());
            model.addLessOrEqual(objective,baselineCost);
            for(var a:r.baseline()) {
                var v=vars.get(a.task()); model.addHint(v.start(),a.start()); model.addHint(v.end(),a.end());
                v.presence().forEach((id,p)->model.addHint(p,id.equals(a.resource())));
            }
        }
        model.minimize(objective);
        String invalid=model.validate(); if(!invalid.isEmpty()) throw new IllegalArgumentException("CP-SAT model invalid: "+invalid);
        long buildMillis=(System.nanoTime()-begin)/1000000;
        CpSolver solver=new CpSolver();
        solver.getParameters().setNumWorkers(r.workers()).setRandomSeed(r.seed())
                .setMaxTimeInSeconds(Math.max(.01,r.seconds()-buildMillis/1000.0)).setLogSearchProgress(false);
        CpSolverStatus status=solver.solve(model);
        var assignments=new ArrayList<Assignment>(); Long cost=null; boolean baselineUsed=false;
        if(status==CpSolverStatus.FEASIBLE || status==CpSolverStatus.OPTIMAL) {
            for(var t:r.tasks()) {
                var v=vars.get(t.id()); String resource=v.presence().entrySet().stream()
                        .filter(x->solver.booleanValue(x.getValue())).map(Map.Entry::getKey).findFirst().orElseThrow();
                assignments.add(new Assignment(t.id(),resource,solver.value(v.start()),solver.value(v.end())));
            }
            cost=Protocol.verify(r,assignments);
            // Integral business score is authoritative; a rounded double is never used for acceptance.
            if(Math.abs(solver.objectiveValue()-cost)>Math.max(1e-5,Math.ulp((double)cost)*2))
                throw new IllegalStateException("Objective/evaluator mismatch");
        } else if(status==CpSolverStatus.UNKNOWN && baselineCost!=null) {
            assignments.addAll(r.baseline()); cost=baselineCost; baselineUsed=true;
        }
        if(baselineCost!=null && cost!=null && cost>baselineCost) throw new IllegalStateException("Baseline regression");
        return new Response(VERSION,r.attempt(),status.name(),solver.solutionInfo(),assignments,cost,
                solver.bestObjectiveBound(),solver.wallTime(),buildMillis,solver.numBranches(),solver.numConflicts(),baselineUsed);
    }

    private void machine(Resource resource) {
        var ts=request.tasks().stream().filter(t->vars.get(t.id()).presence().containsKey(resource.id())).toList();
        if(ts.isEmpty()) return;
        long maximumLoad=0; var loadExpr=LinearExpr.newBuilder();
        for(var t:ts) {
            var option=t.options().stream().filter(o->o.resource().equals(resource.id())).findFirst().orElseThrow();
            maximumLoad=Math.addExact(maximumLoad,option.processing()/60000);
            loadExpr.addTerm(vars.get(t.id()).presence().get(resource.id()),option.processing()/60000);
        }
        if(request.weights().load()>0) {
            IntVar load=integer(maximumLoad,"load"), squared=integer(Math.multiplyExact(maximumLoad,maximumLoad),"squaredLoad");
            model.addEquality(load,loadExpr); model.addMultiplicationEquality(squared,new LinearArgument[]{load,load});
            term(squared,request.weights().load());
        }
        // Circuit encodes actual adjacency, not every pair of different materials.
        CircuitConstraint circuit=model.addCircuit(); BoolVar empty=model.newBoolVar("emptyMachine"); circuit.addArc(0,0,empty);
        var presences=ts.stream().map(t->vars.get(t.id()).presence().get(resource.id())).toArray(BoolVar[]::new);
        model.addEquality(LinearExpr.sum(presences),0).onlyEnforceIf(empty);
        model.addGreaterOrEqual(LinearExpr.sum(presences),1).onlyEnforceIf(empty.not());
        for(int i=0;i<ts.size();i++) {
            var a=ts.get(i); var av=vars.get(a.id()); BoolVar ap=av.presence().get(resource.id());
            circuit.addArc(i+1,i+1,ap.not());
            BoolVar first=model.newBoolVar("first"),last=model.newBoolVar("last");
            circuit.addArc(0,i+1,first); circuit.addArc(i+1,0,last);
            model.addImplication(first,ap); model.addImplication(last,ap);
            for(int j=0;j<ts.size();j++) if(i!=j) {
                var b=ts.get(j); var bv=vars.get(b.id()); BoolVar bp=bv.presence().get(resource.id());
                BoolVar arc=model.newBoolVar("successor"); circuit.addArc(i+1,j+1,arc);
                model.addImplication(arc,ap); model.addImplication(arc,bp);
                model.addGreaterOrEqual(bv.start(),plus(av.end(),b.frozen()==null?b.resourceGap():0)).onlyEnforceIf(arc);
                if(!Objects.equals(a.material(),b.material())) term(arc,request.weights().setup());
                if(a.frozen()==null && b.frozen()==null && a.priority()<b.priority() && request.weights().priority()>0) {
                    BoolVar inversion=model.newBoolVar("priorityInversion"), after=model.newBoolVar("after");
                    model.addGreaterThan(av.start(),bv.start()).onlyEnforceIf(after);
                    model.addLessOrEqual(av.start(),bv.start()).onlyEnforceIf(after.not());
                    model.addMultiplicationEquality(inversion,new LinearArgument[]{ap,bp,after});
                    term(inversion,Math.multiplyExact((long)b.priority()-a.priority(),request.weights().priority()));
                }
            }
        }
    }
}
