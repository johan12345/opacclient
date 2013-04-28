package de.geeksfactory.opacclient.frontend;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.acra.ACRA;
import org.holoeverywhere.widget.Spinner;
import org.json.JSONException;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

import de.geeksfactory.opacclient.OpacClient;
import de.geeksfactory.opacclient.OpacTask;
import de.geeksfactory.opacclient.R;
import de.geeksfactory.opacclient.apis.OpacApi;
import de.geeksfactory.opacclient.barcode.BarcodeScanIntegrator;
import de.geeksfactory.opacclient.objects.Account;
import de.geeksfactory.opacclient.storage.AccountDataSource;
import de.geeksfactory.opacclient.storage.MetaDataSource;
import de.geeksfactory.opacclient.storage.SQLMetaDataSource;

public class SearchFragment extends OpacFragment {

	private SharedPreferences sp;
	private List<ContentValues> cbMg_data;
	private List<ContentValues> cbZst_data;
	private List<ContentValues> cbZstHome_data;
	private boolean advanced = false;
	private Set<String> fields;
	private LoadMetaDataTask lmdt;
	public boolean metaDataLoading = false;
	private long last_meta_try = 0;
	private View view;
	private Bundle initial_query;

	public void urlintent() {
		Uri d = getActivity().getIntent().getData();

		if (d.getHost().equals("de.geeksfactory.opacclient")) {
			String medianr = d.getQueryParameter("id");

			if (medianr != null) {
				Intent intent = new Intent(getActivity(),
						SearchResultDetailsActivity.class);
				intent.putExtra("item_id", medianr);
				startActivity(intent);
				getActivity().finish();
				return;
			}

			String titel = d.getQueryParameter("titel");
			String verfasser = d.getQueryParameter("verfasser");
			String schlag_a = d.getQueryParameter("schlag_a");
			String schlag_b = d.getQueryParameter("schlag_b");
			String isbn = d.getQueryParameter("isbn");
			String jahr_von = d.getQueryParameter("jahr_von");
			String jahr_bis = d.getQueryParameter("jahr_bis");
			String verlag = d.getQueryParameter("verlag");
			Intent myIntent = new Intent(getActivity(),
					SearchResultsActivity.class);
			myIntent.putExtra("titel", (titel != null ? titel : ""));
			myIntent.putExtra("verfasser", (verfasser != null ? verfasser : ""));
			myIntent.putExtra("schlag_a", (schlag_a != null ? schlag_a : ""));
			myIntent.putExtra("schlag_b", (schlag_b != null ? schlag_b : ""));
			myIntent.putExtra("isbn", (isbn != null ? isbn : ""));
			myIntent.putExtra("jahr_von", (jahr_von != null ? jahr_von : ""));
			myIntent.putExtra("jahr_bis", (jahr_bis != null ? jahr_bis : ""));
			myIntent.putExtra("verlag", (verlag != null ? verlag : ""));
			startActivity(myIntent);
			getActivity().finish();
		} else if (d.getHost().equals("opacapp.de")) {
			String[] split = d.getPath().split(":");
			String bib;
			try {
				bib = URLDecoder.decode(split[1], "UTF-8");
			} catch (UnsupportedEncodingException e) {
				bib = URLDecoder.decode(split[1]);
			}

			if (!app.getLibrary().getIdent().equals(bib)) {
				AccountDataSource adata = new AccountDataSource(getActivity());
				adata.open();
				List<Account> accounts = adata.getAllAccounts(bib);
				adata.close();
				if (accounts.size() > 0) {
					app.setAccount(accounts.get(0).getId());
				} else {
					Intent i = new Intent(Intent.ACTION_VIEW,
							Uri.parse("http://opacapp.de/web" + d.getPath()));
					startActivity(i);
					return;
				}
			}
			String medianr = split[2];
			if (medianr.length() > 1) {
				Intent intent = new Intent(getActivity(),
						SearchResultDetailsActivity.class);
				intent.putExtra("item_id", medianr);
				startActivity(intent);
			} else {
				String title;
				try {
					title = URLDecoder.decode(split[3], "UTF-8");
				} catch (UnsupportedEncodingException e) {
					title = URLDecoder.decode(split[3]);
				}
				Bundle query = new Bundle();
				query.putString(OpacApi.KEY_SEARCH_QUERY_TITLE, title);
				Intent intent = new Intent(getActivity(),
						SearchResultsActivity.class);
				intent.putExtra("query", query);
				startActivity(intent);
			}
			getActivity().finish();
			return;
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent idata) {
		super.onActivityResult(requestCode, resultCode, idata);

		// Barcode
		BarcodeScanIntegrator.ScanResult scanResult = BarcodeScanIntegrator
				.parseActivityResult(requestCode, resultCode, idata);
		if (resultCode != Activity.RESULT_CANCELED && scanResult != null) {
			if (scanResult.getContents() == null)
				return;
			if (scanResult.getContents().length() < 3)
				return;

			// Try to determine whether it is an ISBN number or something
			// library
			// internal
			int target_field = 0;
			if (scanResult.getFormatName() != null) {
				if (scanResult.getFormatName().equals("EAN_13")
						&& scanResult.getContents().startsWith("97")) {
					target_field = R.id.etISBN;
				} else if (scanResult.getFormatName().equals("CODE_39")) {
					target_field = R.id.etBarcode;
				}
			}
			if (target_field == 0) {
				if (scanResult.getContents().length() == 13
						&& (scanResult.getContents().startsWith("978") || scanResult
								.getContents().startsWith("979"))) {
					target_field = R.id.etISBN;
				} else if (scanResult.getContents().length() == 10
						&& is_valid_isbn10(scanResult.getContents()
								.toCharArray())) {
					target_field = R.id.etISBN;
				} else {
					target_field = R.id.etBarcode;
				}
			}
			if (target_field == R.id.etBarcode
					&& !fields.contains(OpacApi.KEY_SEARCH_QUERY_BARCODE)) {
				Toast.makeText(getActivity(),
						R.string.barcode_internal_not_supported,
						Toast.LENGTH_LONG).show();
			} else {
				((EditText) view.findViewById(target_field)).setText(scanResult
						.getContents());
				manageVisibility();
			}

		}
	}

	private static boolean is_valid_isbn10(char[] digits) {
		int a = 0;
		for (int i = 0; i < 10; i++) {
			a += i * Integer.parseInt(String.valueOf(digits[i]));
		}
		return a % 11 == Integer.parseInt(String.valueOf(digits[9]));
	}

	@Override
	public void onStart() {
		super.onStart();
		accountSelected();
	}

	protected void manageVisibility() {
		PackageManager pm = getActivity().getPackageManager();
		if (fields.contains(OpacApi.KEY_SEARCH_QUERY_FREE)) {
			view.findViewById(R.id.tvSearchAdvHeader).setVisibility(
					View.VISIBLE);
			view.findViewById(R.id.rlSimpleSearch).setVisibility(View.VISIBLE);
		} else {
			view.findViewById(R.id.tvSearchAdvHeader).setVisibility(View.GONE);
			view.findViewById(R.id.rlSimpleSearch).setVisibility(View.GONE);
		}
		if (fields.contains(OpacApi.KEY_SEARCH_QUERY_TITLE)) {
			view.findViewById(R.id.etTitel).setVisibility(View.VISIBLE);
			view.findViewById(R.id.tvTitel).setVisibility(View.VISIBLE);
		} else {
			view.findViewById(R.id.etTitel).setVisibility(View.GONE);
			view.findViewById(R.id.tvTitel).setVisibility(View.GONE);
		}
		if (fields.contains(OpacApi.KEY_SEARCH_QUERY_AUTHOR)) {
			view.findViewById(R.id.etVerfasser).setVisibility(View.VISIBLE);
			view.findViewById(R.id.tvVerfasser).setVisibility(View.VISIBLE);
		} else {
			view.findViewById(R.id.etVerfasser).setVisibility(View.GONE);
			view.findViewById(R.id.tvVerfasser).setVisibility(View.GONE);
		}
		if (fields.contains(OpacApi.KEY_SEARCH_QUERY_KEYWORDA) && advanced) {
			view.findViewById(R.id.llSchlag).setVisibility(View.VISIBLE);
			view.findViewById(R.id.tvSchlag).setVisibility(View.VISIBLE);
		} else {
			view.findViewById(R.id.llSchlag).setVisibility(View.GONE);
			view.findViewById(R.id.tvSchlag).setVisibility(View.GONE);
		}
		if (fields.contains(OpacApi.KEY_SEARCH_QUERY_KEYWORDB) && advanced) {
			view.findViewById(R.id.etSchlagB).setVisibility(View.VISIBLE);
		} else {
			view.findViewById(R.id.etSchlagB).setVisibility(View.GONE);
		}
		if (fields.contains(OpacApi.KEY_SEARCH_QUERY_BRANCH)) {
			view.findViewById(R.id.llBranch).setVisibility(View.VISIBLE);
			view.findViewById(R.id.tvZweigstelle).setVisibility(View.VISIBLE);
		} else {
			view.findViewById(R.id.llBranch).setVisibility(View.GONE);
			view.findViewById(R.id.tvZweigstelle).setVisibility(View.GONE);
		}
		if (fields.contains(OpacApi.KEY_SEARCH_QUERY_HOME_BRANCH)) {
			view.findViewById(R.id.llHomeBranch).setVisibility(View.VISIBLE);
			view.findViewById(R.id.tvHomeBranch).setVisibility(View.VISIBLE);
		} else {
			view.findViewById(R.id.llHomeBranch).setVisibility(View.GONE);
			view.findViewById(R.id.tvHomeBranch).setVisibility(View.GONE);
		}
		if (fields.contains(OpacApi.KEY_SEARCH_QUERY_CATEGORY)) {
			view.findViewById(R.id.llMediengruppe).setVisibility(View.VISIBLE);
			view.findViewById(R.id.tvMediengruppe).setVisibility(View.VISIBLE);
		} else {
			view.findViewById(R.id.llMediengruppe).setVisibility(View.GONE);
			view.findViewById(R.id.tvMediengruppe).setVisibility(View.GONE);
		}

		EditText etBarcode = (EditText) view.findViewById(R.id.etBarcode);
		String etBarcodeText = etBarcode.getText().toString();
		if (fields.contains(OpacApi.KEY_SEARCH_QUERY_BARCODE)
				&& (advanced || !etBarcodeText.equals(""))) {
			etBarcode.setVisibility(View.VISIBLE);
		} else {
			etBarcode.setVisibility(View.GONE);
		}

		if (fields.contains(OpacApi.KEY_SEARCH_QUERY_ISBN)) {
			view.findViewById(R.id.etISBN).setVisibility(View.VISIBLE);
		} else {
			view.findViewById(R.id.etISBN).setVisibility(View.GONE);
		}

		if (fields.contains(OpacApi.KEY_SEARCH_QUERY_ISBN)
				|| (fields.contains(OpacApi.KEY_SEARCH_QUERY_BARCODE) && (advanced || !etBarcodeText
						.equals("")))) {
			if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
				view.findViewById(R.id.ivBarcode).setVisibility(View.VISIBLE);
			} else {
				view.findViewById(R.id.ivBarcode).setVisibility(View.GONE);
			}
			view.findViewById(R.id.tvBarcodes).setVisibility(View.VISIBLE);
			view.findViewById(R.id.llBarcodes).setVisibility(View.VISIBLE);
		} else {
			view.findViewById(R.id.tvBarcodes).setVisibility(View.GONE);
			view.findViewById(R.id.llBarcodes).setVisibility(View.GONE);
		}

		if (fields.contains(OpacApi.KEY_SEARCH_QUERY_YEAR_RANGE_START)
				&& fields.contains(OpacApi.KEY_SEARCH_QUERY_YEAR_RANGE_END)) {
			view.findViewById(R.id.llJahr).setVisibility(View.VISIBLE);
			view.findViewById(R.id.tvJahr).setVisibility(View.VISIBLE);
			view.findViewById(R.id.etJahr).setVisibility(View.GONE);
		} else if (fields.contains(OpacApi.KEY_SEARCH_QUERY_YEAR)) {
			view.findViewById(R.id.llJahr).setVisibility(View.GONE);
			view.findViewById(R.id.etJahr).setVisibility(View.VISIBLE);
			view.findViewById(R.id.tvJahr).setVisibility(View.VISIBLE);
		} else {
			view.findViewById(R.id.llJahr).setVisibility(View.GONE);
			view.findViewById(R.id.tvJahr).setVisibility(View.GONE);
			view.findViewById(R.id.etJahr).setVisibility(View.GONE);
		}
		if (fields.contains(OpacApi.KEY_SEARCH_QUERY_SYSTEM) && advanced) {
			view.findViewById(R.id.etSystematik).setVisibility(View.VISIBLE);
			view.findViewById(R.id.tvSystematik).setVisibility(View.VISIBLE);
		} else {
			view.findViewById(R.id.etSystematik).setVisibility(View.GONE);
			view.findViewById(R.id.tvSystematik).setVisibility(View.GONE);
		}
		if (fields.contains(OpacApi.KEY_SEARCH_QUERY_AUDIENCE) && advanced) {
			view.findViewById(R.id.etInteressenkreis).setVisibility(
					View.VISIBLE);
			view.findViewById(R.id.tvInteressenkreis).setVisibility(
					View.VISIBLE);
		} else {
			view.findViewById(R.id.etInteressenkreis).setVisibility(View.GONE);
			view.findViewById(R.id.tvInteressenkreis).setVisibility(View.GONE);
		}
		if (fields.contains(OpacApi.KEY_SEARCH_QUERY_PUBLISHER) && advanced) {
			view.findViewById(R.id.etVerlag).setVisibility(View.VISIBLE);
			view.findViewById(R.id.tvVerlag).setVisibility(View.VISIBLE);
		} else {
			view.findViewById(R.id.etVerlag).setVisibility(View.GONE);
			view.findViewById(R.id.tvVerlag).setVisibility(View.GONE);
		}
		if (fields.contains("order") && advanced) {
			view.findViewById(R.id.cbOrder).setVisibility(View.VISIBLE);
			view.findViewById(R.id.tvOrder).setVisibility(View.VISIBLE);
		} else {
			view.findViewById(R.id.cbOrder).setVisibility(View.GONE);
			view.findViewById(R.id.tvOrder).setVisibility(View.GONE);
		}
	}

	public void accountSelected() {
		if (app.getLibrary() == null)
			return;

		metaDataLoading = false;

		fields = new HashSet<String>(Arrays.asList(app.getApi()
				.getSearchFields()));

		advanced = sp.getBoolean("advanced", false);

		manageVisibility();
		fillComboBoxes();
		loadingIndicators();
		restoreInstance();
	}

	private void fillComboBoxes() {
		Spinner cbZst = (Spinner) view.findViewById(R.id.cbBranch);
		Spinner cbZstHome = (Spinner) view.findViewById(R.id.cbHomeBranch);

		MetaDataSource data = new SQLMetaDataSource(getActivity());
		data.open();

		ContentValues all = new ContentValues();
		all.put("key", "");
		all.put("value", getString(R.string.all));

		cbZst_data = data.getMeta(app.getLibrary().getIdent(),
				MetaDataSource.META_TYPE_BRANCH);
		cbZst_data.add(0, all);
		cbZst.setAdapter(new MetaAdapter(getActivity(), cbZst_data,
				R.layout.simple_spinner_item));

		cbZstHome_data = data.getMeta(app.getLibrary().getIdent(),
				MetaDataSource.META_TYPE_HOME_BRANCH);
		int selected = 0;
		String selection;
		if (sp.contains(OpacClient.PREF_HOME_BRANCH_PREFIX))
			selection = sp.getString(OpacClient.PREF_HOME_BRANCH_PREFIX
					+ app.getAccount().getId(), "");
		else {
			try {
				selection = app.getLibrary().getData().getString("homebranch");
			} catch (JSONException e) {
				selection = "";
			}
		}
		int i = 0;
		for (ContentValues row : cbZstHome_data) {
			if (row.getAsString("key").equals(selection)) {
				selected = i;
			}
			i++;
		}
		cbZstHome.setAdapter(new MetaAdapter(getActivity(), cbZstHome_data,
				R.layout.simple_spinner_item));
		cbZstHome.setSelection(selected);

		Spinner cbMg = (Spinner) view.findViewById(R.id.cbMediengruppe);
		cbMg_data = data.getMeta(app.getLibrary().getIdent(),
				MetaDataSource.META_TYPE_CATEGORY);
		cbMg_data.add(0, all);
		cbMg.setAdapter(new MetaAdapter(getActivity(), cbMg_data,
				R.layout.simple_spinner_item));

		if ((cbZst_data.size() == 1 || !fields
				.contains(OpacApi.KEY_SEARCH_QUERY_BRANCH))
				&& (cbMg_data.size() == 1 || !fields
						.contains(OpacApi.KEY_SEARCH_QUERY_CATEGORY))
				&& (cbZstHome_data.size() == 0 || !fields
						.contains(OpacApi.KEY_SEARCH_QUERY_HOME_BRANCH))) {
			loadMetaData(app.getLibrary().getIdent(), true);
			loadingIndicators();
		}

		data.close();
	}

	private void loadingIndicators() {
		int visibility = metaDataLoading ? View.VISIBLE : View.GONE;
		view.findViewById(R.id.pbBranch).setVisibility(visibility);
		view.findViewById(R.id.pbHomeBranch).setVisibility(visibility);
		view.findViewById(R.id.pbMediengruppe).setVisibility(visibility);
	}

	@Override
	public void onAttach(Activity activity) {
		if (getActivity().getIntent().getAction() != null) {
			if (getActivity().getIntent().getAction()
					.equals("android.intent.action.VIEW")) {
				urlintent();
				return;
			}
		}
		super.onAttach(activity);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		view = inflater.inflate(R.layout.search_fragment, container, false);

		sp = PreferenceManager.getDefaultSharedPreferences(getActivity());

		if (getActivity().getIntent().getBooleanExtra("barcode", false)) {
			BarcodeScanIntegrator integrator = new BarcodeScanIntegrator(
					getActivity());
			integrator.initiateScan();
		}
		ArrayAdapter<CharSequence> order_adapter = ArrayAdapter
				.createFromResource(getActivity(), R.array.orders,
						R.layout.simple_spinner_item);
		order_adapter
				.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
		((Spinner) view.findViewById(R.id.cbOrder)).setAdapter(order_adapter);

		ImageView ivBarcode = (ImageView) view.findViewById(R.id.ivBarcode);
		ivBarcode.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				BarcodeScanIntegrator integrator = new BarcodeScanIntegrator(
						SearchFragment.this);
				integrator.initiateScan();
			}
		});

		return view;
	}

	protected void restoreInstance() {
		if (initial_query != null) {
			initEtFromBundle(initial_query, OpacApi.KEY_SEARCH_QUERY_FREE,
					R.id.etSimpleSearch);
			initEtFromBundle(initial_query, OpacApi.KEY_SEARCH_QUERY_TITLE,
					R.id.etTitel);
			initEtFromBundle(initial_query, OpacApi.KEY_SEARCH_QUERY_AUTHOR,
					R.id.etVerfasser);
			initEtFromBundle(initial_query, OpacApi.KEY_SEARCH_QUERY_ISBN,
					R.id.etISBN);
			initEtFromBundle(initial_query, OpacApi.KEY_SEARCH_QUERY_BARCODE,
					R.id.etBarcode);
			initEtFromBundle(initial_query, OpacApi.KEY_SEARCH_QUERY_YEAR,
					R.id.etJahr);
			initEtFromBundle(initial_query,
					OpacApi.KEY_SEARCH_QUERY_YEAR_RANGE_START, R.id.etJahrVon);
			initEtFromBundle(initial_query,
					OpacApi.KEY_SEARCH_QUERY_YEAR_RANGE_END, R.id.etJahrBis);
			initEtFromBundle(initial_query, OpacApi.KEY_SEARCH_QUERY_KEYWORDA,
					R.id.etSchlagA);
			initEtFromBundle(initial_query, OpacApi.KEY_SEARCH_QUERY_KEYWORDB,
					R.id.etSchlagB);
			initEtFromBundle(initial_query, OpacApi.KEY_SEARCH_QUERY_SYSTEM,
					R.id.etSystematik);
			initEtFromBundle(initial_query, OpacApi.KEY_SEARCH_QUERY_AUDIENCE,
					R.id.etInteressenkreis);
			initEtFromBundle(initial_query, OpacApi.KEY_SEARCH_QUERY_PUBLISHER,
					R.id.etVerlag);

			if (initial_query.containsKey(OpacApi.KEY_SEARCH_QUERY_BRANCH)) {
				int selected = 0;
				int i = 0;
				String selection = initial_query
						.getString(OpacApi.KEY_SEARCH_QUERY_BRANCH);
				for (ContentValues row : cbZst_data) {
					if (row.getAsString("key").equals(selection)) {
						selected = i;
					}
					i++;
				}
				((Spinner) view.findViewById(R.id.cbBranch))
						.setSelection(selected);
			}
			if (initial_query.containsKey(OpacApi.KEY_SEARCH_QUERY_HOME_BRANCH)) {
				int selected = 0;
				int i = 0;
				String selection = initial_query
						.getString(OpacApi.KEY_SEARCH_QUERY_HOME_BRANCH);
				for (ContentValues row : cbZstHome_data) {
					if (row.getAsString("key").equals(selection)) {
						selected = i;
					}
					i++;
				}
				((Spinner) view.findViewById(R.id.cbHomeBranch))
						.setSelection(selected);
			}
			if (initial_query.containsKey(OpacApi.KEY_SEARCH_QUERY_CATEGORY)) {
				int selected = 0;
				int i = 0;
				String selection = initial_query
						.getString(OpacApi.KEY_SEARCH_QUERY_CATEGORY);
				for (ContentValues row : cbMg_data) {
					if (row.getAsString("key").equals(selection)) {
						selected = i;
					}
					i++;
				}
				((Spinner) view.findViewById(R.id.cbMediengruppe))
						.setSelection(selected);
			}

		}
	}

	protected void initEtFromBundle(Bundle query, String key, int resid) {
		if (query.containsKey(key))
			((EditText) view.findViewById(resid)).setText(query.getString(key));
	}

	protected void bundleAddTextFromEt(Bundle query, String key, int resid) {
		String str = ((EditText) view.findViewById(resid)).getEditableText()
				.toString();
		if (!str.equals(""))
			query.putString(key, str);
	}

	protected void bundleAddText(Bundle query, String key, String str) {
		if (!str.equals(""))
			query.putString(key, str);
	}

	public Bundle toQuery() {
		String zst = "";
		String mg = "";
		String zst_home = "";
		if (cbZst_data.size() > 1)
			zst = cbZst_data.get(
					((Spinner) view.findViewById(R.id.cbBranch))
							.getSelectedItemPosition()).getAsString("key");
		if (cbZstHome_data.size() > 0) {
			zst_home = cbZstHome_data.get(
					((Spinner) view.findViewById(R.id.cbHomeBranch))
							.getSelectedItemPosition()).getAsString("key");
			sp.edit()
					.putString(
							OpacClient.PREF_HOME_BRANCH_PREFIX
									+ app.getAccount().getId(), zst_home)
					.commit();
		}
		if (cbMg_data.size() > 1)
			mg = cbMg_data.get(
					((Spinner) view.findViewById(R.id.cbMediengruppe))
							.getSelectedItemPosition()).getAsString("key");

		Bundle query = new Bundle();

		bundleAddTextFromEt(query, OpacApi.KEY_SEARCH_QUERY_FREE,
				R.id.etSimpleSearch);
		bundleAddTextFromEt(query, OpacApi.KEY_SEARCH_QUERY_TITLE, R.id.etTitel);
		bundleAddTextFromEt(query, OpacApi.KEY_SEARCH_QUERY_AUTHOR,
				R.id.etVerfasser);
		bundleAddText(query, OpacApi.KEY_SEARCH_QUERY_BRANCH, zst);
		bundleAddText(query, OpacApi.KEY_SEARCH_QUERY_HOME_BRANCH, zst_home);
		bundleAddText(query, OpacApi.KEY_SEARCH_QUERY_CATEGORY, mg);
		bundleAddTextFromEt(query, OpacApi.KEY_SEARCH_QUERY_ISBN, R.id.etISBN);
		bundleAddTextFromEt(query, OpacApi.KEY_SEARCH_QUERY_BARCODE,
				R.id.etBarcode);
		bundleAddTextFromEt(query, OpacApi.KEY_SEARCH_QUERY_YEAR, R.id.etJahr);
		bundleAddTextFromEt(query, OpacApi.KEY_SEARCH_QUERY_YEAR_RANGE_START,
				R.id.etJahrVon);
		bundleAddTextFromEt(query, OpacApi.KEY_SEARCH_QUERY_YEAR_RANGE_END,
				R.id.etJahrBis);
		if (advanced) {
			bundleAddTextFromEt(query, OpacApi.KEY_SEARCH_QUERY_KEYWORDA,
					R.id.etSchlagA);
			bundleAddTextFromEt(query, OpacApi.KEY_SEARCH_QUERY_KEYWORDB,
					R.id.etSchlagB);
			bundleAddTextFromEt(query, OpacApi.KEY_SEARCH_QUERY_SYSTEM,
					R.id.etSystematik);
			bundleAddTextFromEt(query, OpacApi.KEY_SEARCH_QUERY_AUDIENCE,
					R.id.etInteressenkreis);
			bundleAddTextFromEt(query, OpacApi.KEY_SEARCH_QUERY_PUBLISHER,
					R.id.etVerlag);
			bundleAddText(
					query,
					"order",
					(((Integer) ((Spinner) view.findViewById(R.id.cbOrder))
							.getSelectedItemPosition()) + 1) + "");
		}

		return query;
	}

	public void go() {
		Bundle query = toQuery();
		app.startSearch(getActivity(), query);
	}

	public SearchFragment() {
		setHasOptionsMenu(true);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu,
			com.actionbarsherlock.view.MenuInflater inflater) {
		inflater.inflate(R.menu.fragment_search, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_search_go) {
			go();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	public void loadMetaData(String lib) {
		loadMetaData(lib, false);
	}

	public void loadMetaData(String lib, boolean force) {
		if (metaDataLoading)
			return;
		if (System.currentTimeMillis() - last_meta_try < 3600) {
			return;
		}
		last_meta_try = System.currentTimeMillis();
		MetaDataSource data = new SQLMetaDataSource(getActivity());
		data.open();
		boolean fetch = !data.hasMeta(lib);
		data.close();
		if (fetch || force) {
			metaDataLoading = true;
			lmdt = new LoadMetaDataTask();
			lmdt.execute(app, lib);
		}
	}

	public class LoadMetaDataTask extends OpacTask<Boolean> {
		private boolean success = true;
		private long account;

		@Override
		protected Boolean doInBackground(Object... arg0) {
			super.doInBackground(arg0);

			String lib = (String) arg0[1];
			account = app.getAccount().getId();

			try {
				if (lib.equals(app.getLibrary(lib).getIdent())) {
					app.getNewApi(app.getLibrary(lib)).start();
				} else {
					app.getApi().start();
				}
				success = true;
			} catch (java.net.UnknownHostException e) {
				success = false;
			} catch (java.net.SocketException e) {
				success = false;
			} catch (Exception e) {
				ACRA.getErrorReporter().handleException(e);
				success = false;
			}
			return success;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if (account == app.getAccount().getId()) {
				metaDataLoading = false;
				loadingIndicators();
				if (success)
					fillComboBoxes();
			}
		}
	}

	@Override
	public void nfcReceived(String scanResult) {
		if (fields.contains(OpacApi.KEY_SEARCH_QUERY_BARCODE)) {
			((EditText) view.findViewById(R.id.etBarcode)).setText(scanResult);
			manageVisibility();
		} else {
			Toast.makeText(getActivity(),
					R.string.barcode_internal_not_supported, Toast.LENGTH_LONG)
					.show();
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Bundle query = toQuery();
		outState.putBundle("query", query);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {
			if (savedInstanceState.containsKey("query")) {
				initial_query = savedInstanceState.getBundle("query");
			}
		}
	}

}
