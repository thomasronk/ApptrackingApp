package com.example.apptrackingapp;

import java.util.Iterator;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.location.Criteria;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.NumberPicker.OnValueChangeListener;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

public class MainActivity extends Activity {

	String TAG = "ApptrackActivity";
	int MAX_TBF_VALUE = 5;
	int MIN_TBF_VALUE = 1;
	
	private NumberPicker tbf_picker;
	private Button btn_start_tracking;
	private Button btn_stop_tracking;
	private Button btn_delete_assistance;
	private TextView latitude_val ;
	private TextView longitude_val ;
	private TextView latitude_val_nw;
	private TextView longitude_val_nw;
	private TextView ttff_val;
	private TableLayout measure_table;
	
	Thread FirstFixListener;
	private static Handler handler;
	AlertDialog.Builder gps_failure_alert;
	
	private LocationManager loc_mgr;
	private String best_provider;
	private Location location;
	
	private int TBF_value;
	private double lat_value;
	private double long_value;
	private int TTFF = 0;
	private boolean hasGPSfixStart;
	private boolean firstFixReceived;
	private double lat_value_nw;
	private double long_value_nw;
	int maxSatellites;
	float satArray[][] = new float [32][32];
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		Log.d(TAG, "onCreate");
		
		tbf_picker = (NumberPicker)findViewById(R.id.tbf_picker);
		btn_start_tracking = (Button)findViewById(R.id.btn_start_tracking);	
		btn_stop_tracking = (Button)findViewById(R.id.btn_stop_tracking);
		btn_delete_assistance = (Button)findViewById(R.id.btn_del_data);
		latitude_val = (TextView)findViewById(R.id.lat_value);
		longitude_val = (TextView)findViewById(R.id.long_value);
		ttff_val = (TextView)findViewById(R.id.ttff_val);
		latitude_val_nw = (TextView)findViewById(R.id.lat_value_nw);
		longitude_val_nw = (TextView)findViewById(R.id.long_value_nw);
		measure_table = (TableLayout)findViewById(R.id.tableLayout1);
		
		tbf_picker.setMaxValue(MAX_TBF_VALUE);
		tbf_picker.setMinValue(MIN_TBF_VALUE);
		
		loc_mgr = (LocationManager)getSystemService(LOCATION_SERVICE);
		gps_failure_alert = new AlertDialog.Builder(this);
		
		checkGPS();
		
		tbf_picker.setOnValueChangedListener(new OnValueChangeListener() {
			
			@Override
			public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
				// TODO Auto-generated method stub
				Log.d(TAG, "Value Changed Old Value, New Value" + oldVal + newVal);
				TBF_value = newVal;
			}
		});
		
		
		btn_start_tracking.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				Log.d(TAG, "Starting Tracking ACtivity with TBF"+ TBF_value);
				
				//Proofing the app to avoid random button/touches in between tracking
				btn_start_tracking.setEnabled(false);
				btn_stop_tracking.setEnabled(true);
				btn_delete_assistance.setEnabled(false);
				tbf_picker.setEnabled(false);
				latitude_val.setText("");
				longitude_val.setText("");
				ttff_val.setText("");
				
				//Location	
				Criteria criteria = new Criteria();
				best_provider = loc_mgr.getBestProvider(criteria, false);
				Log.d(TAG, "Provider used is "+ best_provider);
				
				loc_mgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, TBF_value*1000, 0, GPSLocationChangeListener);
				loc_mgr.addGpsStatusListener(GPSStatusListener);
				//loc_mgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, TBF_value*1000, 0, NWLocationChangelistener);
				hasGPSfixStart = true;
				ThreadFixListenerIntialise();
				startFixListenerThread();
				
			}
		});
		
		btn_stop_tracking.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				Log.d(TAG, "Stopping Tracking ACtivity");
				
				StopGPSActivity();
			}
		});
		
		btn_delete_assistance.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				Log.d(TAG, "Deleting Assistance Data");
				
				ProgressDialog waiting =  ProgressDialog.show(MainActivity.this, "Deleting", "Please Wait..deleting");
				loc_mgr.sendExtraCommand(LocationManager.GPS_PROVIDER, "delete_aiding_data", null);
				
				latitude_val.setText("");
				longitude_val.setText("");
				ttff_val.setText("");
				firstFixReceived = false;
				waiting.dismiss();
				
			}
		});
		
		//Handler to recieve the Test Failure Message from thread if Fix is not made at all.
		handler = new Handler(){
			@Override
			public void handleMessage(Message msg) {
				// TODO Auto-generated method stub
				Log.d(TAG, "Stopping test due to no fixes");
				
				gps_failure_alert.setMessage("No First Fix was able to be made");
				gps_failure_alert.setTitle("Failure");
				gps_failure_alert.show();
				
				StopGPSActivity();
				super.handleMessage(msg);
			}
		};
	}
	
	public void ThreadFixListenerIntialise(){
		//Thread listening to the First Fix
		FirstFixListener = new Thread(new Runnable(){
			@Override
			public void run() {
				// TODO Auto-generated method stub
				Log.d(TAG, "Thread-Run");
				if(hasGPSfixStart){
					Log.d(TAG, "First Fix Thread: GPS Fix Started");
					//Following loop listens for a fix for 90 seconds
						for(int i=0;i<90;i++){
							try {
								Thread.sleep(1000);
								if(firstFixReceived){
									i=90;
								}
							} 
							catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
						if(!firstFixReceived){
							Log.d(TAG, "Fix not received. Test Failure");
							handler.sendEmptyMessage(0);
						}
				}
			}	
		});
		
	}
	
	public void startFixListenerThread(){
		FirstFixListener.start();
	}
	
	public final GpsStatus.Listener GPSStatusListener = new GpsStatus.Listener() {
		
		@Override
		public void onGpsStatusChanged(int event) {
			// TODO Auto-generated method stub
			GpsStatus gpsStatus =   loc_mgr.getGpsStatus(null);
			switch (event){
				case GpsStatus.GPS_EVENT_FIRST_FIX: 
	                Log.d(TAG, "onGpsStatusChanged(): time to first fix in ms = " + gpsStatus.getTimeToFirstFix());
	                TTFF = (gpsStatus.getTimeToFirstFix())/1000;
	                firstFixReceived = true;
	                break;
				case GpsStatus.GPS_EVENT_STOPPED:
					 Log.i(TAG, "onGpsStatusChanged(): GPS stopped");
	                    break;
				case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
					Iterable<GpsSatellite> satellites = gpsStatus.getSatellites();
					Iterator<GpsSatellite> it = satellites.iterator();
					Log.d(TAG, "Satelite information received" + satellites);
					
					TableRow tr = new TableRow(MainActivity.this);
					tr.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
					
					TextView test = new TextView(MainActivity.this);
					//test.setText("Test1");
					
					TextView test2 = new TextView(MainActivity.this);
					//test2.setText("Test2");
					
	                //tr.addView(test);
	                //tr.addView(test2);
	                
					measure_table.addView(tr);
					
					maxSatellites = 0 ;
					while (it.hasNext()){
						GpsSatellite oSat = (GpsSatellite) it.next();
						satArray[maxSatellites][0] = oSat.getPrn();
						satArray[maxSatellites][1] = oSat.getAzimuth();
	                    satArray[maxSatellites][2] = oSat.getElevation();
	                    satArray[maxSatellites][3] = oSat.getSnr();
	                    maxSatellites++;
	                    
	                    test.setText(String.valueOf(satArray[maxSatellites][0]));
	                   //test2.setText(String.valueOf(satArray[maxSatellites][1]));
	                    tr.addView(test);
		               // tr.addView(test2);
		               
	                    Log.d(TAG, "Satellite"+satArray[maxSatellites][0]+satArray[maxSatellites][1]);
					} 
					
			}
		}
	};
	
	 public LocationListener GPSLocationChangeListener=new LocationListener() {
			public void onStatusChanged(String provider, int status, Bundle extras) {
				// TODO Auto-generated method stub
				Log.d(TAG, "GPSLocationChangeListener: onStatusChanged");
				loc_mgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, TBF_value*1000, 0, this);
			}
			public void onProviderEnabled(String provider) {
				// TODO Auto-generated method stub
			}
			public void onProviderDisabled(String provider) {
				// TODO Auto-generated method stub
			}
			public void onLocationChanged(Location location) {
				// TODO Auto-generated method stub
				Log.d(TAG, "GPSLocationChangeListener: onLocationChanged");
				if((location!=null) && (firstFixReceived == false)){
					firstFixReceived = true;
					Log.d(TAG, "First Fix Received");
				}
				if((location==null)&&(firstFixReceived = true)){
					Log.d(TAG, "GPS Signal Lost");
				}
				loc_mgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, TBF_value*1000, 0, this);	
				updateLocation(location);
			}
		};
		
		
		public LocationListener NWLocationChangelistener=new LocationListener() {
			public void onStatusChanged(String provider, int status, Bundle extras) {
				// TODO Auto-generated method stub
				Log.d(TAG, "NWLocationChangelistener: onStatusChanged");
				loc_mgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, TBF_value*1000, 0, this);
			}
			public void onProviderEnabled(String provider) {
				// TODO Auto-generated method stub
			}
			public void onProviderDisabled(String provider) {
				// TODO Auto-generated method stub
			}
			public void onLocationChanged(Location location) {
				// TODO Auto-generated method stub
				Log.d(TAG, "NWLocationChangelistener:onLocationChanged");
				loc_mgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, TBF_value*1000, 0, this);	
				updateNWLocation(location);
			}
		};
		
		
		public void updateLocation(Location location){
			//Displaying the LatLong Values from GPS Provider to UI
			Log.d(TAG, "updateLocation");
			lat_value = location.getLatitude();
			long_value = location.getLongitude();
			
			latitude_val.setText(String.valueOf(lat_value));
			longitude_val.setText(String.valueOf(long_value));
			ttff_val.setText(String.valueOf(TTFF));
		}
		
		public void updateNWLocation(Location location){
			//Displaying the LatLong Values from Network Provider to UI
			Log.d(TAG, "updateLocation");
			lat_value_nw = location.getLatitude();
			long_value_nw = location.getLongitude();
			
			latitude_val_nw.setText(String.valueOf(lat_value));
			longitude_val_nw.setText(String.valueOf(long_value_nw));
		}
		
		public void StopGPSActivity(){
			Log.d(TAG, "Stopping GPS activity");
			//Stop the scenario and restore defaults
			
			btn_start_tracking.setEnabled(true);
			btn_stop_tracking.setEnabled(false);
			btn_delete_assistance.setEnabled(true);
			tbf_picker.setEnabled(true);
			
			firstFixReceived = false;
			FirstFixListener = null;
			
			//Stop Location Updates
			Log.d(TAG, "Removing Location Listeners");
			loc_mgr.removeUpdates(GPSLocationChangeListener);
			//loc_mgr.removeUpdates(NWLocationChangelistener);
			loc_mgr.removeGpsStatusListener(GPSStatusListener);
		}
		
		public void checkGPS(){
			Log.d(TAG, "checkGPS");
			
			String provider = Settings.Secure.getString(getApplicationContext().getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
			if(!provider.contains("gps"))
			{
				Log.d(TAG, "GPS Disabled");
				switchGPSon();
			}
			else{
				Log.d(TAG, "GPS Active");
			}
		}
		
		public void switchGPSon(){
			Log.d(TAG, "switchGPSon");
			
			final Intent poke = new Intent();
	        poke.setClassName("com.android.settings", "com.android.settings.widget.SettingsAppWidgetProvider"); 
	        poke.addCategory(Intent.CATEGORY_ALTERNATIVE);
	        poke.setData(Uri.parse("3")); 
	        getApplicationContext().sendBroadcast(poke);
		}
		
		@Override
		public boolean onCreateOptionsMenu(Menu menu) {
			// Inflate the menu; this adds items to the action bar if it is present.
			getMenuInflater().inflate(R.menu.main, menu);
			return true;
		}
	
}
