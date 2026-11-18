package android.os;
import java.util.ArrayList;
public final class Handler {
    private static final ArrayList<Runnable> pending=new ArrayList<>();
    public Handler(Looper looper) {}
    public void postDelayed(Runnable task,long delay) { pending.add(task); }
    public void removeCallbacks(Runnable task) { pending.removeIf(item -> item==task); }
    public static void drain() {
        ArrayList<Runnable> tasks=new ArrayList<>(pending); pending.clear();
        for(Runnable task:tasks) task.run();
    }
}
