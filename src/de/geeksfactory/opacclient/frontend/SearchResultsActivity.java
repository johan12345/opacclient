package de.geeksfactory.opacclient.frontend;

import android.content.Intent;
import android.os.Bundle;
import de.geeksfactory.opacclient.R;
import de.geeksfactory.opacclient.frontend.SearchResultsFragment;
import de.geeksfactory.opacclient.objects.SearchResult;

public class SearchResultsActivity extends SublevelFragmentActivity implements
		SearchResultsFragment.OnItemSelected {

	protected SearchResultsFragment frResultList;
	protected SearchResultDetailsFragment frResultDetails;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.search_results_activity);

		if (savedInstanceState != null)
			frResultList = (SearchResultsFragment) getSupportFragmentManager()
					.getFragment(savedInstanceState, "SearchResults_ResultList");
		if (frResultList == null)
			frResultList = SearchResultsFragment.newInstance(getIntent()
					.getBundleExtra("query"), 1);
		frResultList.setListener(this);
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.frameResultList, frResultList).commit();

	}

	@Override
	public void onItemSelected(SearchResult result) {
		if (findViewById(R.id.frameResultDetails) != null) {
			if (result.getId() != null)
				frResultDetails = SearchResultDetailsFragment.newInstance(
						(int) result.getNr(), result.getId());
			else
				frResultDetails = SearchResultDetailsFragment.newInstance(
						(int) result.getNr(), null);

			getSupportFragmentManager().beginTransaction()
					.replace(R.id.frameResultDetails, frResultDetails).commit();
		} else {
			Intent intent = new Intent(this, SearchResultDetailsActivity.class);
			intent.putExtra("item", (int) result.getNr());

			if (result.getId() != null)
				intent.putExtra("item_id", result.getId());
			startActivity(intent);
		}
	}
}
