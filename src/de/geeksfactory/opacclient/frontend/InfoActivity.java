package de.geeksfactory.opacclient.frontend;

public class InfoActivity extends ToplevelFragmentActivity {

	@Override
	protected OpacFragment newFragment() {
		return new InfoFragment();
	}

}
