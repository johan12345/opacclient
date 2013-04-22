package de.geeksfactory.opacclient.frontend;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import de.geeksfactory.opacclient.R;
import de.geeksfactory.opacclient.apis.OpacApi;

public class SearchActivity extends ToplevelFragmentActivity {

	private SharedPreferences sp;

	private String[][] techListsArray;
	private IntentFilter[] intentFiltersArray;
	private PendingIntent nfcIntent;
	private boolean nfc_capable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
	private android.nfc.NfcAdapter mAdapter;

	private OpacFragment mContent;

	private HashSet<String> fields;

	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(R.string.search);
		
		sp = PreferenceManager.getDefaultSharedPreferences(this);

		if (nfc_capable) {
			if (!getPackageManager().hasSystemFeature("android.hardware.nfc")) {
				nfc_capable = false;
			}
		}
		if (nfc_capable) {
			mAdapter = android.nfc.NfcAdapter.getDefaultAdapter(this);
			nfcIntent = PendingIntent.getActivity(this, 0, new Intent(this,
					getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
			IntentFilter ndef = new IntentFilter(
					android.nfc.NfcAdapter.ACTION_TECH_DISCOVERED);
			try {
				ndef.addDataType("*/*");
			} catch (MalformedMimeTypeException e) {
				throw new RuntimeException("fail", e);
			}
			intentFiltersArray = new IntentFilter[] { ndef, };
			techListsArray = new String[][] { new String[] { android.nfc.tech.NfcV.class
					.getName() } };
		}
	}

	@SuppressLint("NewApi")
	@Override
	public void onPause() {
		super.onPause();
		if (nfc_capable && sp.getBoolean("nfc_search", false)) {
			mAdapter.disableForegroundDispatch(this);
		}
	}

	@SuppressLint("NewApi")
	@Override
	public void onResume() {
		super.onResume();
		fields = new HashSet<String>(Arrays.asList(app.getApi()
				.getSearchFields()));

		if (!fields.contains(OpacApi.KEY_SEARCH_QUERY_BARCODE))
			nfc_capable = false;

		if (nfc_capable && sp.getBoolean("nfc_search", false)) {
			mAdapter.enableForegroundDispatch(this, nfcIntent,
					intentFiltersArray, techListsArray);
		}
	}

	@SuppressLint("NewApi")
	@Override
	public void onNewIntent(Intent intent) {
		if (nfc_capable && sp.getBoolean("nfc_search", false)) {
			android.nfc.Tag tag = intent
					.getParcelableExtra(android.nfc.NfcAdapter.EXTRA_TAG);
			String scanResult = readPageToString(tag);
			if (scanResult != null) {
				if (scanResult.length() > 5) {
					mContent.nfcReceived(scanResult);
				}
			}
		}
	}

	/**
	 * Reads the first four blocks of an ISO 15693 NFC tag as ASCII bytes into a
	 * string.
	 * 
	 * @return String Tag memory as a string (bytes converted as ASCII) or
	 *         <code>null</code>
	 */
	@SuppressLint("NewApi")
	public static String readPageToString(android.nfc.Tag tag) {
		byte[] id = tag.getId();
		android.nfc.tech.NfcV tech = android.nfc.tech.NfcV.get(tag);
		byte[] readCmd = new byte[3 + id.length];
		readCmd[0] = 0x20; // set "address" flag (only send command to this
		// tag)
		readCmd[1] = 0x20; // ISO 15693 Single Block Read command byte
		System.arraycopy(id, 0, readCmd, 2, id.length); // copy ID
		StringBuilder stringbuilder = new StringBuilder();
		try {
			tech.connect();
			for (int i = 0; i < 4; i++) {
				readCmd[2 + id.length] = (byte) i; // 1 byte payload: block
													// address
				byte[] data;
				data = tech.transceive(readCmd);
				for (int j = 0; j < data.length; j++) {
					if (data[j] > 32 && data[j] < 127) // We only want printable
														// characters, there
														// might be some
														// nullbytes in it
														// otherwise.
						stringbuilder.append((char) data[j]);
				}
			}
			tech.close();
		} catch (IOException e) {
			try {
				tech.close();
			} catch (IOException e1) {
			}
			return null;
		}
		return stringbuilder.toString().trim();
	}

	@Override
	protected OpacFragment newFragment() {
		return new SearchFragment();
	}

}
