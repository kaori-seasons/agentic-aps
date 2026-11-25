package cn.windwheel.aps.cpsat;
import java.util.*;
import org.junit.jupiter.api.Test;
import static cn.windwheel.aps.cpsat.Protocol.*;
import static org.junit.jupiter.api.Assertions.*;
class CalendarSegmentsTest {
    @Test void everyMillisecondMatchesIndependentWorkingTimeSimulation() {
        var random=new Random(17);
        for(int sample=0;sample<50;sample++) {
            var windows=new ArrayList<Window>(); long time=0;
            for(int i=0;i<4;i++) {long end=time+1+random.nextInt(10);windows.add(new Window(time,end));time=end+random.nextInt(5);}
            for(long duration=1;duration<15;duration++) {
                var pieces=Protocol.segments(windows,duration);
                for(long start=0;start<=time;start++) {
                    long expected=-1,remaining=duration;
                    boolean valid=false;
                    for(var w:windows) {
                        if(start>=w.start() && start<w.end()) valid=true;
                        if(!valid || w.end()<=start) continue;
                        long usable=w.end()-Math.max(w.start(),start);
                        if(remaining<=usable) {expected=Math.max(w.start(),start)+remaining;break;}
                        remaining-=usable;
                    }
                    long actual=-1;int count=0;
                    for(var piece:pieces) if(start>=piece.firstStart() && start<=piece.lastStart()) {actual=start+piece.elapsed();count++;}
                    assertTrue(count<=1); assertEquals(expected,actual,"start="+start+", duration="+duration+", windows="+windows);
                }
            }
        }
    }
}
