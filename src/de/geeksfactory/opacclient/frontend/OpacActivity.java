package de.geeksfactory.opacclient.frontend;

import com.actionbarsherlock.app.SherlockFragmentActivity;

import android.content.Context;
import de.geeksfactory.opacclient.OpacClient;

public interface OpacActivity {
	public SherlockFragmentActivity getActivity();

	public OpacClient getOpacApplication();

	public void accountSelected();

}
