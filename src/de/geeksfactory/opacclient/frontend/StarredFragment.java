package de.geeksfactory.opacclient.frontend;

import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

import de.geeksfactory.opacclient.OpacClient;
import de.geeksfactory.opacclient.R;
import de.geeksfactory.opacclient.apis.OpacApi;
import de.geeksfactory.opacclient.frontend.SearchResultsFragment.OnItemSelected;
import de.geeksfactory.opacclient.objects.SearchResult;
import de.geeksfactory.opacclient.objects.Starred;
import de.geeksfactory.opacclient.storage.StarDataSource;
import de.geeksfactory.opacclient.storage.StarDatabase;

public class StarredFragment extends OpacFragment implements
		LoaderCallbacks<Cursor> {

	private ItemListAdapter adapter;
	private View view;

	private OnItemSelected listener;

	public void setListener(OnItemSelected listener) {
		this.listener = listener;
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		view = inflater.inflate(R.layout.starred_fragment, container, false);

		adapter = new ItemListAdapter();

		ListView lv = (ListView) view.findViewById(R.id.lvStarred);

		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				Starred item = (Starred) view.findViewById(R.id.ivDelete)
						.getTag();
				if(listener != null){
					listener.onItemSelected(item);
				}
			}
		});
		lv.setClickable(true);
		lv.setTextFilterEnabled(true);

		getActivity().getSupportLoaderManager().initLoader(0, null, this);
		lv.setAdapter(adapter);
		return view;
	}

	public StarredFragment() {
		setHasOptionsMenu(true);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu,
			com.actionbarsherlock.view.MenuInflater inflater) {
		inflater.inflate(R.menu.fragment_starred, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_export) {
			export();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void accountSelected() {
		getActivity().getSupportLoaderManager().restartLoader(0, null, this);
	}

	public void remove(Starred item) {
		StarDataSource data = new StarDataSource((ToplevelFragmentActivity) getActivity());
		data.remove(item);
	}

	private class ItemListAdapter extends SimpleCursorAdapter {

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			Starred item = StarDataSource.cursorToItem(cursor);

			TextView tv = (TextView) view.findViewById(R.id.tvTitle);
			if (item.getTitle() != null)
				tv.setText(Html.fromHtml(item.getTitle()));
			else
				tv.setText("");

			ImageView iv = (ImageView) view.findViewById(R.id.ivDelete);
			iv.setFocusableInTouchMode(false);
			iv.setFocusable(false);
			iv.setTag(item);
			iv.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View arg0) {
					remove((Starred) arg0.getTag());
				}
			});
		}

		public ItemListAdapter() {
			super(getActivity(), R.layout.starred_item, null,
					new String[] { "bib" }, null, 0);
		}
	}

	@Override
	public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
		return new CursorLoader(getActivity(), app.getStarProviderStarUri(),
				StarDatabase.COLUMNS, StarDatabase.STAR_WHERE_LIB,
				new String[] { app.getLibrary().getIdent() }, null);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
		adapter.swapCursor(cursor);
		if (cursor.getCount() == 0)
			view.findViewById(R.id.tvWelcome).setVisibility(View.VISIBLE);
		else
			view.findViewById(R.id.tvWelcome).setVisibility(View.GONE);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> arg0) {
		adapter.swapCursor(null);
	}

	public interface OnItemSelected {
		public void onItemSelected(Starred item);
	}
	
	protected void export() {
		Intent intent = new Intent(android.content.Intent.ACTION_SEND);
		intent.setType("text/plain");
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

		StringBuilder text = new StringBuilder();

		StarDataSource data = new StarDataSource((OpacActivity) getActivity());
		List<Starred> items = data.getAllItems(app.getLibrary().getIdent());
		for (Starred item : items) {
			text.append(item.getTitle());
			text.append("\n");
			String shareUrl = app.getApi().getShareUrl(item.getMNr(),
					item.getTitle());
			if (shareUrl != null) {
				text.append(shareUrl);
				text.append("\n");
			}
			text.append("\n");
		}

		intent.putExtra(Intent.EXTRA_TEXT, text.toString().trim());
		startActivity(Intent.createChooser(intent,
				getResources().getString(R.string.share)));
	}
}
