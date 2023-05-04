package club.smileboy.caffeine;
/**
 * @author FLJ
 * @date 2022/9/6
 * @time 15:39
 * @Description fork join exception handler
 */
public  class ForkJoinWorkerThreadExceptionHandler implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            System.out.println("出现异常,但是不处理 !!!!");
        }
    }