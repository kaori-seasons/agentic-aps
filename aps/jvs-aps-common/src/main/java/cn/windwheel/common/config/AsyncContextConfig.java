package cn.windwheel.common.config;

import cn.windwheel.common.utils.SystemThreadLocal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 为异步任务显式复制请求上下文，避免租户与用户信息在求解线程中丢失或串线。
 */
@Slf4j
@Configuration
public class AsyncContextConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        int processors = Runtime.getRuntime().availableProcessors();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("jvs-aps-async-");
        executor.setCorePoolSize(Math.max(4, processors));
        executor.setMaxPoolSize(Math.max(8, processors * 2));
        executor.setQueueCapacity(200);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setTaskDecorator(new SystemThreadLocalTaskDecorator());
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncUncaughtExceptionHandler() {
            @Override
            public void handleUncaughtException(Throwable ex, Method method, Object... params) {
                log.error("异步任务执行异常, method={}", method.getName(), ex);
            }
        };
    }

    private static class SystemThreadLocalTaskDecorator implements TaskDecorator {

        @Override
        public Runnable decorate(Runnable runnable) {
            Map<String, Object> contextMap = copy(SystemThreadLocal.get());
            return () -> {
                Map<String, Object> previousContext = copy(SystemThreadLocal.get());
                try {
                    if (contextMap == null) {
                        SystemThreadLocal.clear();
                    } else {
                        SystemThreadLocal.setAll(new HashMap<>(contextMap));
                    }
                    runnable.run();
                } finally {
                    if (previousContext == null) {
                        SystemThreadLocal.clear();
                    } else {
                        SystemThreadLocal.setAll(previousContext);
                    }
                }
            };
        }

        private Map<String, Object> copy(Map<String, Object> source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            return new HashMap<>(source);
        }
    }
}
