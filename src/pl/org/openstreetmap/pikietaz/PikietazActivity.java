package pl.org.openstreetmap.pikietaz;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.GpsStatus.Listener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

/* File based on org.osm.kaypadmapper2
 * Thanks to God for OpenSource! :-)
 */

public class PikietazActivity extends Activity implements OnClickListener, LocationListener, Listener {
	private static final int REQUEST_GPS_ENABLE = 1;
	private EditText ref_edit;
	private EditText pk_edit;
	private TextView status_label;
	private Button add_button;
	private String basename;
	private LocationManager locationManager = null;
	private static final long locationUpdateMinTimeMs = 0; // minimum time for location updates in ms
	private static final float locationUpdateMinDistance = 0; // minimum distance for location updates in m
	private Location location;
	private OsmWriter osmWriter = null;
	
	private int lastclick=0;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pikietaz);
        // check for GPS
		locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
		if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			showDialogGpsDisabled();
		}
		location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		
		// check for external storage
		String extStorageState = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(extStorageState)) {
			// We can read and write the media
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(extStorageState)) {
			// We can only read the media
			showDialogFatalError(R.string.errorStorageRO);
		} else {
			// Something else is wrong. It may be one of many other states, but all we need to know is we can neither read nor write
			showDialogFatalError(R.string.errorStorageUnavailable);
		}
				
        pk_edit = (EditText) findViewById(R.id.pk_edit);
        ref_edit = (EditText) findViewById(R.id.ref_edit);
        status_label = (TextView) findViewById(R.id.status);
        add_button=(Button) findViewById(R.id.add_button);
        add_button.setOnClickListener(this);
        
        findViewById(R.id.plus_button).setOnClickListener(this);
        findViewById(R.id.minus_button).setOnClickListener(this);
        
        status_label.setText("Hello");
        
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, locationUpdateMinTimeMs, locationUpdateMinDistance, this);
		locationManager.addGpsStatusListener(this);
				
		String extStorage = System.getenv().get("EXTERNAL_STORAGE");
		File kpmFolder = new File(extStorage+"/pikietaz");
		if (!kpmFolder.exists()) {
			if (!kpmFolder.mkdir()) {
				showDialogFatalError(R.string.FolderCreationFailed);
			}
		}
		Calendar cal = Calendar.getInstance();
		basename = String.format("%tF_%tH-%tM-%tS", cal, cal, cal, cal);
		
		try {
			String file = kpmFolder + "/" + basename + ".osm";
			Log.i("New file", file);
			osmWriter = new OsmWriter(file, false);
		}  catch (IOException e) {
			showDialogFatalError(R.string.errorFileOpen);
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
    
    @Override
	protected void onPause() {
		super.onPause();
		try {
				osmWriter.flush();
		} catch (IOException e) {
			// something is going horribly wrong. no way out.
		}
	}
    
    @Override
	public void onDestroy() {		
		super.onDestroy();
		locationManager.removeUpdates(this);
		try {
				osmWriter.close();
		} catch (IOException e) {
			// should not happen, the file may be damaged anyway.
		}
	}


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.pikietaz, menu);
        return true;
    }

    private Runnable unlockButton = new Runnable()
    {
        @Override
        public void run()
        {
           add_button.setEnabled(true);
        }
     };

	@Override
	public void onClick(View arg0) {
		switch (arg0.getId()) {
		case R.id.minus_button:
			lastclick=-1;
			pk_edit.setText(""+(Integer.parseInt(pk_edit.getText().toString())-1));
			break;
		case R.id.add_button:
			Log.i("Pikietaz", "new point");
			if (location==null){
				Log.e("Pikietaz", "no location!");
				return;
			}
			HashMap<String, String> tags=new HashMap<String, String>();
			tags.put("highway", "milestone");
			tags.put("pk", pk_edit.getText().toString());
			tags.put("source", "GPS");
			tags.put("ref", ref_edit.getText().toString());
			try {
				osmWriter.addNode(location.getLatitude(), location.getLongitude(), tags);
			} catch (IOException e) {
				showDialogFatalError(R.string.errorFileOpen);
			}
			Toast.makeText(this,"pk=" + pk_edit.getText().toString(), Toast.LENGTH_LONG).show();
			//Auto(increment/decrement)
			pk_edit.setText(""+(Integer.parseInt(pk_edit.getText().toString())+lastclick));
			
			
			Handler myHandler = new Handler();
			add_button.setEnabled(false);
			myHandler.postDelayed(unlockButton, 1000);
			break;
		case R.id.plus_button:
			lastclick=1;
			pk_edit.setText(""+(Integer.parseInt(pk_edit.getText().toString())+1));
			break;
		}
	}
	
	@Override
    public void onBackPressed() {
            super.onBackPressed();
            this.finish();
    }
		
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.quit:
			finish();
			return true;
		default:
		      return super.onOptionsItemSelected(item);
		}
	}

	
	private void showDialogGpsDisabled() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.errorGpsDisabled)
			.setCancelable(false)
			.setNegativeButton(R.string.quit, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					finish();
				}
			})
			.setPositiveButton(R.string.systemSettings, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					startActivityForResult(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_GPS_ENABLE);
				}
		});
		builder.create().show();
	}
	
	private void showDialogFatalError(int messageId) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(messageId)
			.setCancelable(false)
			.setNegativeButton(R.string.quit, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					finish();
				}
		});
		builder.create().show();
	}


	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_GPS_ENABLE:
			LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
			if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
				showDialogGpsDisabled();
			}
			break;
		default:
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	@Override
	public void onGpsStatusChanged(int event) {
		switch (event) {
		case GpsStatus.GPS_EVENT_STARTED:
			status_label.setText(R.string.statusStartedString);
			break;
		case GpsStatus.GPS_EVENT_STOPPED:
			status_label.setText(R.string.statusStoppedString);
			break;
		case GpsStatus.GPS_EVENT_FIRST_FIX:
			status_label.setText(R.string.statusFixString);
			break;
		case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
			GpsStatus gpsStatus = locationManager.getGpsStatus(null);
			int maxSats = 0;
			int usedSats = 0;
			Iterable<GpsSatellite> gpsSatellites = gpsStatus.getSatellites();
			for (GpsSatellite sat : gpsSatellites) {
				maxSats++;
				if (sat.usedInFix()) {
					usedSats++;
				}
			}
			status_label.setText("Sat:"+usedSats+"/"+maxSats);
			break;
		}
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		// useless, doesn't get called when a GPS fix is available
	}

	@Override
	public void onProviderDisabled(String provider) {
		if(provider.equals(LocationManager.GPS_PROVIDER)){
			showDialogGpsDisabled();
		}
	}

	@Override
	public void onProviderEnabled(String provider) {
		// ignored. GPS availability is checked on startup
	}

	@Override
	public void onLocationChanged(Location location) {
		this.location = location;
		status_label.setText( getString(R.string.statusReadyString, location.getAccuracy()));
	}    
}
