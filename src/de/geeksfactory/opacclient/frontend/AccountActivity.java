package de.geeksfactory.opacclient.frontend;

import com.actionbarsherlock.view.Window;

import android.os.Bundle;

public class AccountActivity extends ToplevelFragmentActivity {

	@Override
	protected OpacFragment newFragment() {
		return new AccountFragment();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		super.onCreate(savedInstanceState);
	}

}
