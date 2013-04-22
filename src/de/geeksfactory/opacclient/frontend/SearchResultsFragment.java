package de.geeksfactory.opacclient.frontend;

import org.acra.ACRA;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

import de.geeksfactory.opacclient.NotReachableException;
import de.geeksfactory.opacclient.OpacTask;
import de.geeksfactory.opacclient.R;
import de.geeksfactory.opacclient.objects.SearchRequestResult;
import de.geeksfactory.opacclient.objects.SearchResult;

public class SearchResultsFragment extends OpacFragment {

	protected SearchRequestResult searchresult;
	private SparseArray<SearchRequestResult> cache = new SparseArray<SearchRequestResult>();
	private int page;
	private Bundle query;

	private SearchStartTask st;
	private SearchPageTask sst;

	private View view;
	private ViewGroup container;

	private OnItemSelected listener;

	public void setListener(OnItemSelected listener) {
		this.listener = listener;
	}

	public interface OnItemSelected {
		public void onItemSelected(SearchResult result);
	}

	public static SearchResultsFragment newInstance(Bundle query, int page) {
		SearchResultsFragment fragNew = new SearchResultsFragment();
		fragNew.setRequest(query, page);
		return fragNew;
	}

	public void setRequest(Bundle query, int page) {
		this.query = query;
		this.page = page;
	}

	public SearchResultsFragment() {
		setHasOptionsMenu(true);
	}

	private View setContentView(int resid) {
		if (container == null)
			return null;
		container.removeAllViews();
		view = View.inflate(getActivity(), resid, container);
		return view;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		this.container = container;
		view = inflater.inflate(R.layout.loading, container, false);
		if (searchresult != null) {
			loaded();
		}
		return view;
	}

	@Override
	public void onStart() {
		super.onStart();
		if (searchresult == null)
			performSearch();
	}

	public void performSearch() {
		setContentView(R.layout.loading);
		if (page == 1) {
			st = new SearchStartTask();
			st.execute(app, query);
		} else {
			sst = new SearchPageTask();
			sst.execute(app, page);
		}
	}

	@SuppressLint("NewApi")
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_prev) {
			setContentView(R.layout.loading);
			if (sst != null) {
				sst.cancel(false);
			}
			page--;
			if (cache.get(page) != null) {
				searchresult = cache.get(page);
				loaded();
			} else {
				searchresult = null;
				sst = new SearchPageTask();
				sst.execute(app, page);
			}
			getOpacActivity().getActivity().invalidateOptionsMenu();
			return true;
		} else if (item.getItemId() == R.id.action_next) {
			setContentView(R.layout.loading);
			if (sst != null) {
				sst.cancel(false);
			}
			page++;
			if (cache.get(page) != null) {
				searchresult = cache.get(page);
				loaded();
			} else {
				searchresult = null;
				sst = new SearchPageTask();
				sst.execute(app, page);
			}
			getOpacActivity().getActivity().invalidateOptionsMenu();
			return true;
		} else if (item.getItemId() == android.R.id.home) {
			getOpacActivity().getActivity().finish(); // TODO: hm?
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu,
			com.actionbarsherlock.view.MenuInflater inflater) {
		inflater.inflate(R.menu.fragment_search_results, menu);

		if (page == 1) {
			menu.findItem(R.id.action_prev).setVisible(false);
		} else {

			menu.findItem(R.id.action_prev).setVisible(true);
		}
	}

	public class SearchStartTask extends OpacTask<SearchRequestResult> {
		private boolean success;
		private Exception exception;

		@Override
		protected SearchRequestResult doInBackground(Object... arg0) {
			super.doInBackground(arg0);
			Bundle query = (Bundle) arg0[1];

			try {
				SearchRequestResult res = app.getApi().search(query);
				success = true;
				return res;
			} catch (java.net.UnknownHostException e) {
				success = false;
				exception = e;
				e.printStackTrace();
			} catch (java.net.SocketException e) {
				success = false;
				exception = e;
			} catch (Exception e) {
				exception = e;
				ACRA.getErrorReporter().handleException(e);
				success = false;
			}

			return null;
		}

		@Override
		protected void onPostExecute(SearchRequestResult result) {
			if (success) {
				if (result == null) {

					if (app.getApi().getLast_error().equals("is_a_redirect")) {
						Intent intent = new Intent(getActivity(),
								SearchResultDetailsActivity.class);
						startActivity(intent);
						getActivity().finish();
						return;
					}

					setContentView(R.layout.connectivity_error);
					((TextView) view.findViewById(R.id.tvErrBody)).setText(app
							.getApi().getLast_error());
					((Button) view.findViewById(R.id.btRetry))
							.setOnClickListener(new OnClickListener() {
								@Override
								public void onClick(View v) {
									performSearch();
								}
							});
				} else {
					searchresult = result;
					if (searchresult != null) {
						if (searchresult.getResults().size() > 0) {
							if (searchresult.getResults().get(0).getId() != null)
								cache.put(page, searchresult);
						}
					}
					loaded();
				}
			} else {
				setContentView(R.layout.connectivity_error);
				if (exception != null
						&& exception instanceof NotReachableException)
					((TextView) view.findViewById(R.id.tvErrBody))
							.setText(R.string.connection_error_detail_nre);
				((Button) view.findViewById(R.id.btRetry))
						.setOnClickListener(new OnClickListener() {
							@Override
							public void onClick(View v) {
								performSearch();
							}
						});
			}
		}
	}

	protected void loaded() {
		if (view == null) {
			return;
		}
		setContentView(R.layout.search_results_fragment);

		ListView lv = (ListView) view.findViewById(R.id.lvResults);
		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				if (listener != null)
					listener.onItemSelected(searchresult.getResults().get(
							position));
			}
		});

		TextView rn = (TextView) view.findViewById(R.id.tvResultNum);
		if (searchresult.getTotal_result_count() >= 0)
			rn.setText(getString(R.string.result_number,
					searchresult.getTotal_result_count()));

		if (searchresult.getResults().size() == 0
				&& searchresult.getTotal_result_count() == 0) {
			setContentView(R.layout.no_results);
		}

		lv.setAdapter(new ResultsAdapter(getActivity(), (searchresult
				.getResults())));
		lv.setTextFilterEnabled(true);
	}

	public class SearchPageTask extends OpacTask<SearchRequestResult> {
		private boolean success;

		@Override
		protected SearchRequestResult doInBackground(Object... arg0) {
			super.doInBackground(arg0);
			Integer page = (Integer) arg0[1];

			try {
				SearchRequestResult res = app.getApi().searchGetPage(page);
				success = true;
				return res;
			} catch (java.net.UnknownHostException e) {
				success = false;
				e.printStackTrace();
			} catch (java.net.SocketException e) {
				success = false;
				e.printStackTrace();
			} catch (Exception e) {
				ACRA.getErrorReporter().handleException(e);
				success = false;
			}
			return null;
		}

		@Override
		protected void onPostExecute(SearchRequestResult result) {
			if (success) {
				searchresult = result;
				if (searchresult != null) {
					if (searchresult.getResults().size() > 0) {
						if (searchresult.getResults().get(0).getId() != null)
							cache.put(page, searchresult);
					}
				}
				loaded();
			} else {
				setContentView(R.layout.connectivity_error);
				((Button) view.findViewById(R.id.btRetry))
						.setOnClickListener(new OnClickListener() {
							@Override
							public void onClick(View v) {
								performSearch();
							}
						});
			}
		}
	}

	@Override
	public void onStop() {
		super.onStop();

		try {
			if (st != null) {
				if (!st.isCancelled()) {
					st.cancel(true);
				}
			}
			if (sst != null) {
				if (!sst.isCancelled()) {
					sst.cancel(true);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindDrawables(view.findViewById(R.id.rootView));
		System.gc();
	}

	@Override
	public void accountSelected() {
		// TODO Auto-generated method stub
	}
}
