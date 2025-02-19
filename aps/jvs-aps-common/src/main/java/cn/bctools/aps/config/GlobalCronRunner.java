package cn.bctools.aps.config;

import cn.hutool.cron.CronUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * @author jvs
 */
@Slf4j
@Component
public class GlobalCronRunner implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        CronUtil.setMatchSecond(true);
        CronUtil.start(true);
    }

}