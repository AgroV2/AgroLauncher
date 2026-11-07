package by.agro.launcher.ui.panels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class InstancesAsyncTest {
    @TempDir Path temp;
    @Test void slowScanDoesNotBlockEdtAndStaleResultCannotOverwriteNewer() throws Exception {
        CountDownLatch entered=new CountDownLatch(1), unblock=new CountDownLatch(1), next=new CountDownLatch(1);
        AtomicBoolean ranOnEdt=new AtomicBoolean(); AtomicInteger calls=new AtomicInteger();
        AtomicReference<InstancesPanel> panel=new AtomicReference<>();
        var first=new InstancesPanel.Installation("1.0","vanilla","default",temp.resolve("old"),null,null,0);
        var second=new InstancesPanel.Installation("2.0","vanilla","default",temp.resolve("new"),null,null,0);
        var pool=Executors.newSingleThreadExecutor();
        try {
            SwingUtilities.invokeAndWait(() -> panel.set(new InstancesPanel(null,null,null,()->{
                ranOnEdt.set(SwingUtilities.isEventDispatchThread());
                int n=calls.incrementAndGet();
                if(n==1) {
                    entered.countDown();
                    try {if(!unblock.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("timeout");}
                    catch(InterruptedException e) {Thread.currentThread().interrupt(); throw new IllegalStateException(e);}
                } else next.countDown();
                return new InstancesPanel.ScanResult(List.of(n==1?first:second),List.of());
            })));
            assertTrue(entered.await(3,TimeUnit.SECONDS));
            var edt=pool.submit(()->{SwingUtilities.invokeAndWait(()->panel.get().refresh());return true;});
            assertTrue(edt.get(2,TimeUnit.SECONDS),"EDT should remain responsive while scanning");
            unblock.countDown(); assertTrue(next.await(3,TimeUnit.SECONDS));
            var modelField=InstancesPanel.class.getDeclaredField("installationsModel"); modelField.setAccessible(true);
            @SuppressWarnings("unchecked") var model=(DefaultListModel<InstancesPanel.Installation>)modelField.get(panel.get());
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5); AtomicBoolean loaded=new AtomicBoolean();
            while(System.nanoTime()<deadline && !loaded.get()) {
                SwingUtilities.invokeAndWait(()->loaded.set(model.size()==1 && model.get(0).gameDir.equals(second.gameDir)));
                if(!loaded.get()) Thread.sleep(20);
            }
            assertTrue(loaded.get()); assertFalse(ranOnEdt.get());
        } finally {unblock.countDown();pool.shutdownNow();}
    }
}
