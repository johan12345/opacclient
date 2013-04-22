package de.geeksfactory.opacclient.frontend;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.holoeverywhere.app.AlertDialog;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;
import com.slidingmenu.lib.SlidingMenu;
import com.slidingmenu.lib.SlidingMenu.OnOpenListener;
import com.slidingmenu.lib.app.SlidingFragmentActivity;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import de.geeksfactory.opacclient.OpacClient;
import de.geeksfactory.opacclient.R;
import de.geeksfactory.opacclient.objects.Account;
import de.geeksfactory.opacclient.objects.Library;
import de.geeksfactory.opacclient.storage.AccountDataSource;
import de.geeksfactory.opacclient.storage.StarDataSource;

public abstract class ToplevelFragmentActivity extends SlidingFragmentActivity
		implements OpacActivity {

	protected OpacFragment frContent;
	protected OpacClient app;
	protected AlertDialog adialog;
	protected NavigationFragment mFrag;

	@Override
	public SherlockFragmentActivity getActivity() {
		return this;
	}

	protected abstract OpacFragment newFragment();

	public void accountSelected() {
		frContent.accountSelected();
	}

	@Override
	public void setContentView(int id) {
		super.setContentView(id);
		setupMenu();
	}

	private void setupMenu() {
		SlidingMenu sm = getSlidingMenu();
		if (findViewById(R.id.menu_frame) == null) {
			setBehindContentView(R.layout.menu_frame);

			FragmentTransaction t = this.getSupportFragmentManager()
					.beginTransaction();
			mFrag = app.newNavigationFragment();
			t.replace(R.id.menu_frame, mFrag);
			t.commit();

			// Sliding Menu
			sm.setShadowWidthRes(R.dimen.shadow_width);
			sm.setShadowDrawable(R.drawable.shadow);
			sm.setBehindOffsetRes(R.dimen.slidingmenu_offset);
			sm.setFadeDegree(0.35f);
			sm.setTouchModeAbove(SlidingMenu.TOUCHMODE_FULLSCREEN);
			sm.setOnOpenListener(new OnOpenListener() {
				@Override
				public void onOpen() {
					if (getCurrentFocus() != null) {
						InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
						imm.hideSoftInputFromWindow(getCurrentFocus()
								.getWindowToken(), 0);
					}
				}
			});
			sm.setSlidingEnabled(true);
			// show home as up so we can toggle
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);

			SharedPreferences sp = PreferenceManager
					.getDefaultSharedPreferences(this);

			if (!sp.getBoolean("version2.0.0-introduced", false)) {
				final Handler handler = new Handler();
				// Just show the menu to explain that is there if people start
				// version 2 for the first time.
				// We need a handler because if we just put this in onCreate
				// nothing
				// happens. I don't have any idea, why.
				handler.postDelayed(new Runnable() {
					@Override
					public void run() {
						SharedPreferences sp = PreferenceManager
								.getDefaultSharedPreferences(ToplevelFragmentActivity.this);
						getSlidingMenu().showMenu(true);
						sp.edit().putBoolean("version2.0.0-introduced", true)
								.commit();
					}
				}, 500);
			}

		} else {
			// add a dummy view
			View v = new View(this);
			setBehindContentView(v);
			sm.setSlidingEnabled(false);
			FragmentTransaction t = this.getSupportFragmentManager()
					.beginTransaction();
			mFrag = app.newNavigationFragment();
			t.replace(R.id.menu_frame, mFrag);
			t.commit();
		}
	}

	public OpacClient getOpacApplication() {
		return app;
	}

	protected int getLayoutResource() {
		return R.layout.default_activity;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.getSupportActionBar().setHomeButtonEnabled(true);

		app = (OpacClient) getApplication();

		setContentView(getLayoutResource());

		if (savedInstanceState != null)
			frContent = (SearchFragment) getSupportFragmentManager()
					.getFragment(savedInstanceState,
							"ToplevelFragmentActivity_mContent");
		if (frContent == null)
			frContent = newFragment();
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.content_frame, frContent).commit();
	}

	@Override
	protected void onStart() {
		super.onStart();
		SharedPreferences sp = PreferenceManager
				.getDefaultSharedPreferences(this);
		if (app.getAccount() == null || app.getLibrary() == null) {
			if (!sp.getString("opac_bib", "").equals("")) {
				// Migrate
				Map<String, String> renamed_libs = new HashMap<String, String>();
				renamed_libs.put("Trier (Palais Walderdorff)", "Trier");
				renamed_libs.put("Ludwigshafen (Rhein)", "Ludwigshafen Rhein");
				renamed_libs.put("Neu-Ulm", "NeuUlm");
				renamed_libs.put("Hann. Münden", "HannMünden");
				renamed_libs.put("Münster", "Munster");
				renamed_libs.put("Tübingen", "Tubingen");
				renamed_libs.put("Göttingen", "Gottingen");
				renamed_libs.put("Schwäbisch Hall", "Schwabisch Hall");

				StarDataSource stardata = new StarDataSource(this);
				stardata.renameLibraries(renamed_libs);

				Library lib = null;
				try {
					if (renamed_libs.containsKey(sp.getString("opac_bib", "")))
						lib = app.getLibrary(renamed_libs.get(sp.getString(
								"opac_bib", "")));
					else
						lib = app.getLibrary(sp.getString("opac_bib", ""));
				} catch (Exception e) {
					e.printStackTrace();
				}

				if (lib != null) {
					AccountDataSource data = new AccountDataSource(this);
					data.open();
					Account acc = new Account();
					acc.setLibrary(lib.getIdent());
					acc.setLabel(getString(R.string.default_account_name));
					if (!sp.getString("opac_usernr", "").equals("")) {
						acc.setName(sp.getString("opac_usernr", ""));
						acc.setPassword(sp.getString("opac_password", ""));
					}
					long insertedid = data.addAccount(acc);
					data.close();
					app.setAccount(insertedid);

					Toast.makeText(
							this,
							"Neue Version! Alte Accountdaten wurden wiederhergestellt.",
							Toast.LENGTH_LONG).show();

				} else {
					Toast.makeText(
							this,
							"Neue Version! Wiederherstellung alter Zugangsdaten ist fehlgeschlagen.",
							Toast.LENGTH_LONG).show();
				}
			}
		}
		if (app.getLibrary() == null) {
			// Create new
			app.addFirstAccount(this);
			return;
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		showContent();
	}

	public void selectaccount() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		// Get the layout inflater
		LayoutInflater inflater = getLayoutInflater();

		View view = inflater.inflate(R.layout.simple_list_dialog, null);

		ListView lv = (ListView) view.findViewById(R.id.lvBibs);
		AccountDataSource data = new AccountDataSource(this);
		data.open();
		final List<Account> accounts = data.getAllAccounts();
		data.close();
		AccountListAdapter adapter = new AccountListAdapter(this, accounts);
		lv.setAdapter(adapter);
		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {

				app.setAccount(accounts.get(position).getId());

				adialog.dismiss();

				accountSelected();
			}
		});
		builder.setTitle(R.string.account_select)
				.setView(view)
				.setNegativeButton(R.string.cancel,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								adialog.cancel();
							}
						})
				.setNeutralButton(R.string.accounts_edit,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								dialog.dismiss();
								Intent intent = new Intent(
										ToplevelFragmentActivity.this,
										AccountListActivity.class);
								startActivity(intent);
							}
						});
		adialog = builder.create();
		adialog.show();
	}

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

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			if (getSlidingMenu().isSlidingEnabled())
				toggle();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
}
