package za.co.jpsoft.winkerkreader;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.widget.Toast;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.*;
public class MyService extends Service {

	@Override
	public IBinder onBind(Intent intent) {

		return null;
	}

	@Override
	public void onCreate() {
		Toast.makeText(getBaseContext(),
				"Application Is Running in Background", Toast.LENGTH_SHORT)
				.show();
		boolean isOn = true;
		final SharedPreferences settings = getSharedPreferences(PREFS_USER_INFO, 0);
		final SharedPreferences.Editor editor = settings.edit();
		TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		PhoneStateListener phoneStateListener = new PhoneStateListener() {
			@Override
			public void onCallStateChanged(int state, String incomingNumber) {
				if (state == TelephonyManager.CALL_STATE_RINGING) {
					if (incomingNumber != null) {
						editor.putString("CallerNumber", incomingNumber);} else {
						editor.putString("CallerNumber", "Unknown");}
					editor.commit();
					startService(new Intent(getApplicationContext(), oproepdetail.class));
				} else if (oproepdetail.isOn) {
					final Handler handler = new Handler();
					handler.postDelayed(new Runnable() {
						@Override
						public void run() {
							editor.putString("CallerNumber", "XXXXXXXXXX");
							editor.apply();
							stopService(new Intent(getApplicationContext(), oproepdetail.class));
						}
					}, 10000);
				}
			}
		};
		telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

		super.onCreate();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// TODO Auto-generated method stub
		return super.onStartCommand(intent, flags, startId);
	}

}
