package de.geeksfactory.opacclient.frontend;

import android.os.Bundle;
import de.geeksfactory.opacclient.R;

public class SearchResultDetailsActivity extends SublevelFragmentActivity {

	private OpacFragment frameResultDetails;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.result_details_activity);

		if (savedInstanceState != null)
			frameResultDetails = (SearchFragment) getSupportFragmentManager()
					.getFragment(savedInstanceState, "SearchResults_ResultDetails");
		if (frameResultDetails == null)
			frameResultDetails = SearchResultDetailsFragment.newInstance(
					getIntent().getIntExtra("item", -1), getIntent()
							.getStringExtra("item_id"));
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.frameResultDetails, frameResultDetails).commit();

	}
}
