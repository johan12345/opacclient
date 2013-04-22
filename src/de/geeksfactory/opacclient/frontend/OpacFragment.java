package de.geeksfactory.opacclient.frontend;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.actionbarsherlock.app.SherlockFragment;

import de.geeksfactory.opacclient.OpacClient;

public abstract class OpacFragment extends SherlockFragment {

	public OpacClient app;

	@Override
	public void onAttach(Activity activity) {
		app = (OpacClient) ((OpacActivity) activity).getOpacApplication();
		super.onAttach(activity);
	}

	protected final OpacActivity getOpacActivity() {
		return (OpacActivity) getActivity();
	}

	public void nfcReceived(String scanResult) {

	}

	public abstract void accountSelected();

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
}
