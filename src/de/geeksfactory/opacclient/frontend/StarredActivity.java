package de.geeksfactory.opacclient.frontend;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import de.geeksfactory.opacclient.OpacClient;
import de.geeksfactory.opacclient.R;
import de.geeksfactory.opacclient.apis.OpacApi;
import de.geeksfactory.opacclient.objects.SearchResult;
import de.geeksfactory.opacclient.objects.Starred;

public class StarredActivity extends ToplevelFragmentActivity implements
		StarredFragment.OnItemSelected, SearchResultsFragment.OnItemSelected {

	protected OpacFragment frResultDetails;

	@Override
	protected OpacFragment newFragment() {
		StarredFragment newFrag = new StarredFragment();
		newFrag.setListener(this);
		return newFrag;
	}

	@Override
	protected int getLayoutResource() {
		return R.layout.starred_activity;
	}

	@Override
	public void onItemSelected(Starred item) {

		if (findViewById(R.id.frameResultDetails) != null) {

			if (item.getMNr() == null || item.getMNr().equals("null")
					|| item.getMNr().equals("")) {
				SharedPreferences sp = PreferenceManager
						.getDefaultSharedPreferences(getActivity());
				Bundle query = new Bundle();
				query.putString(OpacApi.KEY_SEARCH_QUERY_TITLE, item.getTitle());
				query.putString(
						OpacApi.KEY_SEARCH_QUERY_HOME_BRANCH,
						sp.getString(OpacClient.PREF_HOME_BRANCH_PREFIX
								+ app.getAccount().getId(), null));
				frResultDetails = SearchResultsFragment.newInstance(query, 1);
				((SearchResultsFragment) frResultDetails).setListener(this);
			} else {
				frResultDetails = SearchResultDetailsFragment.newInstance(-1,
						item.getMNr());
			}
			getSupportFragmentManager().beginTransaction()
					.replace(R.id.frameResultDetails, frResultDetails).commit();

		} else {

			if (item.getMNr() == null || item.getMNr().equals("null")
					|| item.getMNr().equals("")) {
				SharedPreferences sp = PreferenceManager
						.getDefaultSharedPreferences(getActivity());
				Intent myIntent = new Intent(getActivity(),
						SearchResultsActivity.class);
				Bundle query = new Bundle();
				query.putString(OpacApi.KEY_SEARCH_QUERY_TITLE, item.getTitle());
				query.putString(
						OpacApi.KEY_SEARCH_QUERY_HOME_BRANCH,
						sp.getString(OpacClient.PREF_HOME_BRANCH_PREFIX
								+ app.getAccount().getId(), null));
				myIntent.putExtra("query", query);
				startActivity(myIntent);
			} else {
				Intent intent = new Intent(getActivity(),
						SearchResultDetailsActivity.class);
				intent.putExtra("item_id", item.getMNr());
				startActivity(intent);
			}

		}
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
