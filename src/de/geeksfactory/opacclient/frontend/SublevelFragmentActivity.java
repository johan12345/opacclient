package de.geeksfactory.opacclient.frontend;

import org.holoeverywhere.app.AlertDialog;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;

import de.geeksfactory.opacclient.OpacClient;

public abstract class SublevelFragmentActivity extends SherlockFragmentActivity implements OpacActivity {
	protected OpacClient app;
	protected AlertDialog adialog;

	public OpacClient getOpacApplication() {
		return app;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.getSupportActionBar().setHomeButtonEnabled(true);
		this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		app = (OpacClient) getApplication();
	}

	protected void unbindDrawables(View view) {
		if (view == null)
			return;
		if (view.getBackground() != null) {
			view.getBackground().setCallback(null);
		}
		if (view instanceof ViewGroup) {
			for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
				unbindDrawables(((ViewGroup) view).getChildAt(i));
			}
			if (!(view instanceof AdapterView)) {
				((ViewGroup) view).removeAllViews();
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}


	@Override
	public SherlockFragmentActivity getActivity() {
		return this;
	}

	@Override
	public void accountSelected() {
	}
}
