package ca.nbenner.bizprocribbing;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class LocnService extends Service {
    private LocnBinder binder = null;

    @Override public void onCreate() {
        super.onCreate();
        binder = new LocnBinder( );
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        binder.updateSettings(intent);
        return START_REDELIVER_INTENT;
    }
    @Override public IBinder onBind(Intent intent) {
        binder.updateSettings(intent);
        return binder;
    }
    @Override public void onDestroy() {
        binder.closeGoogleApiClient();
    }

}
