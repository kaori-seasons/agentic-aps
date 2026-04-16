package cn.windwheel.aps;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;


@SpringBootApplication
@ComponentScan(basePackages = {"cn.windwheel.*"})
public class JvsApsApplication {

    public static void main(String[] args) {
        SpringApplication.run(JvsApsApplication.class, args);
    }
}