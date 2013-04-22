package de.geeksfactory.opacclient.barcode;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.support.v4.app.Fragment;
import de.geeksfactory.opacclient.R;

public class BarcodeScanIntegrator {
	public static final int REQUEST_CODE_QRDROID = 0x0000094c;
	public static final int REQUEST_CODE_ZXING = 0x0000094d;

	private Activity activity = null;
	private Fragment fragment = null;

	public static class ScanResult {
		private final String contents;
		private final String formatName;

		public ScanResult(String contents, String formatName) {
			this.contents = contents;
			this.formatName = formatName;
		}

		/**
		 * @return raw content of barcode
		 */
		public String getContents() {
			return contents;
		}

		/**
		 * @return name of format, like "QR_CODE", "UPC_A". See
		 *         {@code BarcodeFormat} for more format names.
		 */
		public String getFormatName() {
			return formatName;
		}
	}

	public BarcodeScanIntegrator(Object ctx) {
		if (ctx instanceof Activity) {
			activity = (Activity) ctx;
		} else if (ctx instanceof Fragment) {
			fragment = (Fragment) ctx;
			activity = fragment.getActivity();
		} else {
			throw new RuntimeException(
					"ctx should be either an Activity or a Fragment!");
		}
	}

	public static ScanResult parseActivityResult(int requestCode,
			int resultCode, Intent idata) {
		if (resultCode != Activity.RESULT_OK || idata == null
				|| idata.getExtras() == null) {
			return null;
		}
		if (requestCode == REQUEST_CODE_QRDROID) {
			String result = idata.getExtras().getString("la.droid.qr.result");
			return new ScanResult(result, null);
		} else if (requestCode == REQUEST_CODE_ZXING) {

			String contents = idata.getStringExtra("SCAN_RESULT");
			String formatName = idata.getStringExtra("SCAN_RESULT_FORMAT");
			return new ScanResult(contents, formatName);

		} else {
			return null;
		}
	}

	public void initiateScan() {
		PackageManager pm = activity.getPackageManager();
		try {
			pm.getPackageInfo("com.google.zxing.client.android", 0);
			initiate_scan_zxing();
			return;
		} catch (NameNotFoundException e) {
		}
		try {
			pm.getPackageInfo("com.srowen.bs.android", 0);
			initiate_scan_zxing();
			return;
		} catch (NameNotFoundException e) {
		}
		try {
			pm.getPackageInfo("com.srowen.bs.android.simple", 0);
			initiate_scan_zxing();
			return;
		} catch (NameNotFoundException e) {
		}
		try {
			pm.getPackageInfo("com.srowen.bs.android.simple", 0);
			initiate_scan_zxing();
			return;
		} catch (NameNotFoundException e) {
		}
		try {
			pm.getPackageInfo("la.droid.qr", 0);
			initiate_scan_qrdroid();
			return;
		} catch (NameNotFoundException e) {
		}
		try {
			pm.getPackageInfo("la.droid.qr.priva", 0);
			initiate_scan_qrdroid();
			return;
		} catch (NameNotFoundException e) {
		}
		download_dialog();
	}

	public void download_dialog() {
		AlertDialog.Builder downloadDialog = new AlertDialog.Builder(activity);
		downloadDialog.setTitle(R.string.barcode_title);
		downloadDialog.setMessage(R.string.barcode_desc);
		downloadDialog.setPositiveButton(R.string.barcode_gplay,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
						Uri uri = Uri
								.parse("market://details?id=com.google.zxing.client.android");
						Intent intent = new Intent(Intent.ACTION_VIEW, uri);
						try {
							startActivity(intent);
						} catch (ActivityNotFoundException anfe) {
							// Hmm, market is not installed
						}
					}
				});
		downloadDialog.setNeutralButton(R.string.barcode_amazon,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
						Uri uri = Uri
								.parse("http://www.amazon.com/gp/mas/dl/android?p=com.google.zxing.client.android");
						Intent intent = new Intent(Intent.ACTION_VIEW, uri);
						startActivity(intent);
					}
				});
		downloadDialog.setNegativeButton(R.string.barcode_no,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
					}
				});
		AlertDialog alert = downloadDialog.create();
		alert.show();
	}

	public void initiate_scan_zxing() {
		final Collection<String> desiredBarcodeFormats = list("UPC_A", "UPC_E",
				"EAN_8", "EAN_13", "CODE_39", "CODE_93", "CODE_128");

		Intent intentScan = new Intent("com.google.zxing.client.android.SCAN");
		intentScan.addCategory(Intent.CATEGORY_DEFAULT);

		// check which types of codes to scan for
		if (desiredBarcodeFormats != null) {
			// set the desired barcode types
			StringBuilder joinedByComma = new StringBuilder();
			for (String format : desiredBarcodeFormats) {
				if (joinedByComma.length() > 0) {
					joinedByComma.append(',');
				}
				joinedByComma.append(format);
			}
			intentScan.putExtra("SCAN_FORMATS", joinedByComma.toString());
		}

		String targetAppPackage = findTargetAppPackage(intentScan);
		if (targetAppPackage == null) {
			download_dialog();
			return;
		}
		intentScan.setPackage(targetAppPackage);
		intentScan.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intentScan.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
		startActivityForResult(intentScan, REQUEST_CODE_ZXING);
	}

	private String findTargetAppPackage(Intent intent) {
		final Collection<String> targetApplications = list(
				"com.google.zxing.client.android", "com.srowen.bs.android",
				"com.srowen.bs.android.simple");
		PackageManager pm = activity.getPackageManager();
		List<ResolveInfo> availableApps = pm.queryIntentActivities(intent,
				PackageManager.MATCH_DEFAULT_ONLY);
		if (availableApps != null) {
			for (ResolveInfo availableApp : availableApps) {
				String packageName = availableApp.activityInfo.packageName;
				if (targetApplications.contains(packageName)) {
					return packageName;
				}
			}
		}
		return null;
	}

	public void initiate_scan_qrdroid() {
		Intent qrDroid = new Intent("la.droid.qr.scan");
		qrDroid.putExtra("la.droid.qr.complete", true);
		startActivityForResult(qrDroid, REQUEST_CODE_QRDROID);
	}

	private static Collection<String> list(String... values) {
		return Collections.unmodifiableCollection(Arrays.asList(values));
	}

	private void startActivityForResult(Intent intent, int requestCode) {
		if (fragment != null)
			fragment.startActivityForResult(intent, requestCode);
		else
			activity.startActivityForResult(intent, requestCode);
	}

	private void startActivity(Intent intent) {
		if (fragment != null)
			fragment.startActivity(intent);
		else
			activity.startActivity(intent);
	}
}
