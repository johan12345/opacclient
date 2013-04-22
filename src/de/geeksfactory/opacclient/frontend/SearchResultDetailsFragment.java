package de.geeksfactory.opacclient.frontend;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.List;
import java.util.Map.Entry;

import org.acra.ACRA;
import org.holoeverywhere.app.AlertDialog;
import org.holoeverywhere.app.ProgressDialog;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.TextUtils.TruncateAt;
import android.text.util.Linkify;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

import de.geeksfactory.opacclient.OpacClient;
import de.geeksfactory.opacclient.OpacTask;
import de.geeksfactory.opacclient.R;
import de.geeksfactory.opacclient.apis.OpacApi.ReservationResult;
import de.geeksfactory.opacclient.objects.Account;
import de.geeksfactory.opacclient.objects.Detail;
import de.geeksfactory.opacclient.objects.DetailledItem;
import de.geeksfactory.opacclient.storage.AccountDataSource;
import de.geeksfactory.opacclient.storage.StarDataSource;

public class SearchResultDetailsFragment extends OpacFragment {

	private ProgressDialog dialog;
	private DetailledItem item;
	private String id;
	private String title;

	private FetchTask ft;
	private FetchSubTask fst;
	private ResTask rt;
	private boolean account_switched = false;

	private View view;
	private ViewGroup container;

	private String request_id;
	private int request_num;

	protected AlertDialog adialog;

	@Override
	public void accountSelected() {
		account_switched = true;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		this.container = container;
		view = inflater.inflate(R.layout.loading, container, false);

		getOpacActivity().getActivity().getSupportActionBar()
				.setDisplayHomeAsUpEnabled(true);

		return view;
	}

	public SearchResultDetailsFragment() {
		setHasOptionsMenu(true);
	}

	public static SearchResultDetailsFragment newInstance(int num, String id) {
		SearchResultDetailsFragment newFrag = new SearchResultDetailsFragment();
		newFrag.setRequest(num, id);
		return newFrag;
	}

	public void setRequest(int num, String id) {
		request_num = num;
		request_id = id;
	}

	private void load() {
		if (request_id != null) {
			id = request_id;
			fst = new FetchSubTask();
			fst.execute(app, id);
		} else if (request_num != 0) {
			ft = new FetchTask();
			ft.execute(app, request_num);
		} else {
			fst = new FetchSubTask();
			fst.execute(app, id);
		}
	}

	@Override
	public void onStart() {
		if (item == null)
			load();
		super.onStart();
	}

	protected void dialog_no_credentials() {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setMessage(R.string.status_nouser)
				.setCancelable(false)
				.setNegativeButton(R.string.dismiss,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
							}
						})
				.setPositiveButton(R.string.accounts_edit,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								Intent intent = new Intent(getActivity(),
										AccountEditActivity.class);
								intent.putExtra(
										AccountEditActivity.EXTRA_ACCOUNT_ID,
										app.getAccount().getId());
								startActivity(intent);
							}
						});
		AlertDialog alert = builder.create();
		alert.show();
	}

	protected void reservationStart() {
		AccountDataSource data = new AccountDataSource(getActivity());
		data.open();
		final List<Account> accounts = data.getAccountsWithPassword(app
				.getLibrary().getIdent());
		data.close();
		if (accounts.size() == 0) {
			dialog_no_credentials();
			return;
		} else if (accounts.size() > 1) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			// Get the layout inflater
			LayoutInflater inflater = getActivity().getLayoutInflater();

			View view = inflater.inflate(R.layout.simple_list_dialog, null);

			ListView lv = (ListView) view.findViewById(R.id.lvBibs);
			AccountListAdapter adapter = new AccountListAdapter(getActivity(),
					accounts);
			lv.setAdapter(adapter);
			lv.setOnItemClickListener(new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view,
						int position, long id) {
					if (accounts.get(position).getId() != app.getAccount()
							.getId() || account_switched) {
						app.setAccount(accounts.get(position).getId());
						dialog = ProgressDialog.show(getActivity(), "",
								getString(R.string.doing_res), true);
						dialog.show();
						new RestoreSessionTask().execute();
					} else {
						reservationDo();
					}
					adialog.dismiss();
				}
			});
			builder.setTitle(R.string.account_select)
					.setView(view)
					.setNegativeButton(R.string.cancel,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int id) {
									adialog.cancel();
								}
							})
					.setNeutralButton(R.string.accounts_edit,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int id) {
									adialog.dismiss();
									Intent intent = new Intent(getActivity(),
											AccountListActivity.class);
									startActivity(intent);
								}
							});
			adialog = builder.create();
			adialog.show();
		} else {
			reservationDo();
		}
	}

	public void reservationDo() {
		reservationDo(0, null);
	}

	public void reservationDo(int useraction, String selection) {
		if (dialog == null) {
			dialog = ProgressDialog.show(getActivity(), "",
					getString(R.string.doing_res), true);
			dialog.show();
		} else if (!dialog.isShowing()) {
			dialog = ProgressDialog.show(getActivity(), "",
					getString(R.string.doing_res), true);
			dialog.show();
		}

		rt = new ResTask();
		rt.execute(app, item.getReservation_info(), useraction, selection);
	}

	public void reservationResult(ReservationResult result) {
		AccountDataSource adata = new AccountDataSource(getActivity());
		adata.open();
		adata.invalidateCachedAccountData(app.getAccount());
		adata.close();
		switch (result.getStatus()) {
		case CONFIRMATION_NEEDED:
			reservationConfirmation(result);
			break;
		case SELECTION_NEEDED:
			reservationSelection(result);
			break;
		case ERROR:
			dialog_wrong_credentials(app.getApi().getLast_error(), false);
			break;
		case OK:
			Intent intent = new Intent(getActivity(), AccountActivity.class);
			startActivity(intent);
			break;
		case UNSUPPORTED:
			// TODO: Show dialog
			break;
		default:
			break;
		}
	}

	public class SelectionAdapter extends ArrayAdapter<Object> {

		private Object[] objects;

		@Override
		public View getView(int position, View contentView, ViewGroup viewGroup) {
			View view = null;

			if (objects[position] == null) {
				LayoutInflater layoutInflater = (LayoutInflater) getContext()
						.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				view = layoutInflater.inflate(R.layout.zst_listitem, viewGroup,
						false);
				return view;
			}

			String item = ((Entry<String, Object>) objects[position])
					.getValue().toString();

			if (contentView == null) {
				LayoutInflater layoutInflater = (LayoutInflater) getContext()
						.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				view = layoutInflater.inflate(R.layout.zst_listitem, viewGroup,
						false);
			} else {
				view = contentView;
			}

			TextView tvText = (TextView) view.findViewById(android.R.id.text1);
			tvText.setText(item);
			return view;
		}

		public SelectionAdapter(Context context, Object[] objects) {
			super(context, R.layout.simple_spinner_item, objects);
			this.objects = objects;
		}

	}

	public void reservationSelection(final ReservationResult result) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

		LayoutInflater inflater = getActivity().getLayoutInflater();

		View view = inflater.inflate(R.layout.simple_list_dialog, null);

		ListView lv = (ListView) view.findViewById(R.id.lvBibs);
		final Object[] possibilities = result.getSelection().valueSet()
				.toArray();

		lv.setAdapter(new SelectionAdapter(getActivity(), possibilities));
		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				adialog.dismiss();

				reservationDo(result.getActionIdentifier(),
						((Entry<String, Object>) possibilities[position])
								.getKey());
			}
		});
		switch (result.getActionIdentifier()) {
		case ReservationResult.ACTION_BRANCH:
			builder.setTitle(R.string.zweigstelle);
		}
		builder.setView(view).setNegativeButton(R.string.cancel,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						adialog.cancel();
					}
				});
		adialog = builder.create();
		adialog.show();
	}

	public void reservationConfirmation(final ReservationResult result) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

		LayoutInflater inflater = getActivity().getLayoutInflater();

		View view = inflater.inflate(R.layout.reservation_details_dialog, null);

		TableLayout table = (TableLayout) view.findViewById(R.id.tlDetails);

		for (String[] detail : result.getDetails()) {
			TableRow tr = new TableRow(getActivity());
			if (detail.length == 2) {
				TextView tv1 = new TextView(getActivity());
				tv1.setText(Html.fromHtml(detail[0]));
				tv1.setTypeface(null, Typeface.BOLD);
				tv1.setPadding(0, 0, 8, 0);
				TextView tv2 = new TextView(getActivity());
				tv2.setText(Html.fromHtml(detail[1]));
				tv2.setEllipsize(TruncateAt.END);
				tv2.setSingleLine(false);
				tr.addView(tv1);
				tr.addView(tv2);
			} else if (detail.length == 1) {
				TextView tv1 = new TextView(getActivity());
				tv1.setText(Html.fromHtml(detail[0]));
				tv1.setPadding(0, 2, 0, 2);
				TableRow.LayoutParams params = new TableRow.LayoutParams(0);
				params.span = 2;
				tv1.setLayoutParams(params);
				tr.addView(tv1);
			}
			table.addView(tr);
		}

		builder.setTitle(R.string.confirm_title)
				.setView(view)
				.setPositiveButton(R.string.confirm,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								reservationDo(
										ReservationResult.ACTION_CONFIRMATION,
										"confirmed");
							}
						})
				.setNegativeButton(R.string.cancel,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								adialog.cancel();
							}
						});
		adialog = builder.create();
		adialog.show();
	}

	public class RestoreSessionTask extends OpacTask<Integer> {
		@Override
		protected Integer doInBackground(Object... arg0) {
			try {
				if (id != null) {
					SharedPreferences sp = PreferenceManager
							.getDefaultSharedPreferences(getActivity());
					String homebranch = sp.getString(
							OpacClient.PREF_HOME_BRANCH_PREFIX
									+ app.getAccount().getId(), null);
					app.getApi().getResultById(id, homebranch);
					return 0;
				} else
					ACRA.getErrorReporter().handleException(
							new Throwable("No ID supplied"));
			} catch (java.net.SocketException e) {
				e.printStackTrace();
			} catch (java.net.UnknownHostException e) {
				e.printStackTrace();
			} catch (Exception e) {
				ACRA.getErrorReporter().handleException(e);
			}
			return 1;
		}

		@Override
		protected void onPostExecute(Integer result) {
			reservationDo();
		}

	}

	public class FetchTask extends OpacTask<DetailledItem> {
		protected boolean success = true;

		@Override
		protected DetailledItem doInBackground(Object... arg0) {
			super.doInBackground(arg0);
			Integer nr = (Integer) arg0[1];

			try {
				DetailledItem res = app.getApi().getResult(nr);
				URL newurl;
				if (res.getCover() != null) {
					try {
						newurl = new URL(res.getCover());
						Bitmap mIcon_val = BitmapFactory.decodeStream(newurl
								.openConnection().getInputStream());
						res.setCoverBitmap(mIcon_val);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
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
		@SuppressLint("NewApi")
		protected void onPostExecute(DetailledItem result) {
			if (!success || result == null) {
				setContentView(R.layout.connectivity_error);
				((Button) view.findViewById(R.id.btRetry))
						.setOnClickListener(new OnClickListener() {
							@Override
							public void onClick(View v) {
								load();
							}
						});
				return;
			}

			item = result;

			setContentView(R.layout.result_details_fragment);

			try {
				Log.i("result", item.toString());
			} catch (Exception e) {
				ACRA.getErrorReporter().handleException(e);
			}
			ImageView iv = (ImageView) view.findViewById(R.id.ivCover);

			if (item.getCoverBitmap() != null) {
				iv.setVisibility(View.VISIBLE);
				iv.setImageBitmap(item.getCoverBitmap());
			} else {
				iv.setVisibility(View.GONE);
			}

			TextView tvTitel = (TextView) view.findViewById(R.id.tvTitle);
			tvTitel.setText(item.getTitle());
			title = item.getTitle();

			LinearLayout llDetails = (LinearLayout) view
					.findViewById(R.id.llDetails);
			for (Detail detail : result.getDetails()) {
				View v = getActivity().getLayoutInflater().inflate(
						R.layout.detail_listitem, null);
				((TextView) v.findViewById(R.id.tvDesc)).setText(detail
						.getDesc());
				((TextView) v.findViewById(R.id.tvContent)).setText(detail
						.getContent());
				Linkify.addLinks((TextView) v.findViewById(R.id.tvContent),
						Linkify.WEB_URLS);
				llDetails.addView(v);
			}

			LinearLayout llCopies = (LinearLayout) view
					.findViewById(R.id.llCopies);
			if (result.getVolumesearch() != null) {
				TextView tvC = (TextView) view.findViewById(R.id.tvCopies);
				tvC.setText(R.string.baende);
				Button btnVolume = new Button(getActivity());
				btnVolume.setText(R.string.baende_volumesearch);
				btnVolume.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						Intent myIntent = new Intent(getActivity(),
								SearchResultsActivity.class);
						myIntent.putExtra("query", item.getVolumesearch());
						startActivity(myIntent);
					}
				});
				llCopies.addView(btnVolume);

			} else if (result.getBaende().size() > 0) {
				TextView tvC = (TextView) view.findViewById(R.id.tvCopies);
				tvC.setText(R.string.baende);

				for (final ContentValues band : result.getBaende()) {
					View v = getActivity().getLayoutInflater().inflate(
							R.layout.band_listitem, null);
					((TextView) v.findViewById(R.id.tvTitel)).setText(band
							.getAsString(DetailledItem.KEY_CHILD_TITLE));

					v.findViewById(R.id.llItem).setOnClickListener(
							new OnClickListener() {
								@Override
								public void onClick(View v) {
									Intent intent = new Intent(getActivity(),
											SearchResultDetailsFragment.class);
									intent.putExtra(
											"item_id",
											band.getAsString(DetailledItem.KEY_CHILD_ID));
									startActivity(intent);
								}
							});
					llCopies.addView(v);
				}
			} else {
				if (result.getCopies().size() == 0) {
					view.findViewById(R.id.tvCopies).setVisibility(View.GONE);
				} else {
					for (ContentValues copy : result.getCopies()) {
						View v = getActivity().getLayoutInflater().inflate(
								R.layout.copy_listitem, null);

						if (copy.containsKey(DetailledItem.KEY_COPY_LOCATION)) {
							((TextView) v.findViewById(R.id.tvLocation))
									.setText(copy
											.getAsString(DetailledItem.KEY_COPY_LOCATION));
							((TextView) v.findViewById(R.id.tvLocation))
									.setVisibility(View.VISIBLE);
						} else if (copy
								.containsKey(DetailledItem.KEY_COPY_BARCODE)) {
							((TextView) v.findViewById(R.id.tvLocation))
									.setText(copy
											.getAsString(DetailledItem.KEY_COPY_BARCODE));
							((TextView) v.findViewById(R.id.tvLocation))
									.setVisibility(View.VISIBLE);
						} else {
							((TextView) v.findViewById(R.id.tvLocation))
									.setVisibility(View.GONE);
						}
						if (copy.containsKey(DetailledItem.KEY_COPY_BRANCH)) {
							((TextView) v.findViewById(R.id.tvZst))
									.setText(copy
											.getAsString(DetailledItem.KEY_COPY_BRANCH));
							((TextView) v.findViewById(R.id.tvZst))
									.setVisibility(View.VISIBLE);
						} else {
							((TextView) v.findViewById(R.id.tvZst))
									.setVisibility(View.GONE);
						}
						if (copy.containsKey(DetailledItem.KEY_COPY_STATUS)) {
							((TextView) v.findViewById(R.id.tvStatus))
									.setText(copy
											.getAsString(DetailledItem.KEY_COPY_STATUS));
							((TextView) v.findViewById(R.id.tvStatus))
									.setVisibility(View.VISIBLE);
						} else {
							((TextView) v.findViewById(R.id.tvStatus))
									.setVisibility(View.GONE);
						}
						if (copy.containsKey(DetailledItem.KEY_COPY_RESERVATIONS)) {
							((TextView) v.findViewById(R.id.tvVorbestellt))
									.setText(getString(R.string.res)
											+ ": "
											+ copy.getAsString(DetailledItem.KEY_COPY_RESERVATIONS));
							((TextView) v.findViewById(R.id.tvVorbestellt))
									.setVisibility(View.VISIBLE);
						} else {
							((TextView) v.findViewById(R.id.tvVorbestellt))
									.setVisibility(View.GONE);
						}
						if (copy.containsKey("rueckgabe")) {
							((TextView) v.findViewById(R.id.tvRueckgabe))
									.setText(getString(R.string.ret)
											+ ": "
											+ copy.getAsString(DetailledItem.KEY_COPY_RETURN));
							((TextView) v.findViewById(R.id.tvRueckgabe))
									.setVisibility(View.VISIBLE);
						} else {
							((TextView) v.findViewById(R.id.tvRueckgabe))
									.setVisibility(View.GONE);
						}

						llCopies.addView(v);
					}
				}
			}

			if (id == null || id.equals("")) {
				id = item.getId();
			}

			getOpacActivity().getActivity().invalidateOptionsMenu();
		}
	}

	protected void dialog_wrong_credentials(String s, final boolean finish) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setMessage(getString(R.string.opac_error) + " " + s)
				.setCancelable(false)
				.setNegativeButton(R.string.dismiss,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
								if (finish)
									getActivity().finish();
							}
						})
				.setPositiveButton(R.string.prefs,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								Intent intent = new Intent(getActivity(),
										AccountEditActivity.class);
								intent.putExtra(
										AccountEditActivity.EXTRA_ACCOUNT_ID,
										app.getAccount().getId());
								startActivity(intent);
							}
						});
		AlertDialog alert = builder.create();
		alert.show();
	}

	public class FetchSubTask extends FetchTask {
		@Override
		protected DetailledItem doInBackground(Object... arg0) {
			this.a = (OpacClient) arg0[0];
			String a = (String) arg0[1];

			try {
				SharedPreferences sp = PreferenceManager
						.getDefaultSharedPreferences(getActivity());
				String homebranch = sp.getString(
						OpacClient.PREF_HOME_BRANCH_PREFIX
								+ app.getAccount().getId(), null);
				DetailledItem res = app.getApi().getResultById(a, homebranch);
				URL newurl;
				try {
					newurl = new URL(res.getCover());
					Bitmap mIcon_val = BitmapFactory.decodeStream(newurl
							.openConnection().getInputStream());
					res.setCoverBitmap(mIcon_val);
				} catch (Exception e) {
					e.printStackTrace();
				}
				success = true;
				return res;
			} catch (java.net.UnknownHostException e) {
				publishProgress(e, "ioerror");
			} catch (java.io.IOException e) {
				success = false;
				e.printStackTrace();
			} catch (java.lang.IllegalStateException e) {
				success = false;
				e.printStackTrace();
			} catch (Exception e) {
				publishProgress(e, "ioerror");
			}

			return null;
		}
	}

	private void setContentView(int resid) {
		if (container == null)
			return;
		container.removeAllViews();
		view = View.inflate(getActivity(), resid, container);
	}

	public class ResTask extends OpacTask<ReservationResult> {
		private boolean success;

		@Override
		protected ReservationResult doInBackground(Object... arg0) {
			super.doInBackground(arg0);

			app = (OpacClient) arg0[0];
			String reservation_info = (String) arg0[1];
			int useraction = (Integer) arg0[2];
			String selection = (String) arg0[3];

			try {
				ReservationResult res = app.getApi().reservation(
						reservation_info, app.getAccount(), useraction,
						selection);
				success = true;
				return res;
			} catch (java.net.UnknownHostException e) {
				publishProgress(e, "ioerror");
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
		protected void onPostExecute(ReservationResult res) {
			dialog.dismiss();

			if (!success || res == null) {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						getActivity());
				builder.setMessage(R.string.connection_error)
						.setCancelable(true)
						.setNegativeButton(R.string.dismiss,
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog,
											int id) {
										dialog.cancel();
									}
								});
				AlertDialog alert = builder.create();
				alert.show();
				return;
			}

			reservationResult(res);
		}
	}

	@Override
	public void onStop() {
		super.onStop();
		if (dialog != null) {
			if (dialog.isShowing()) {
				dialog.cancel();
			}
		}
		try {
			if (ft != null) {
				if (!ft.isCancelled()) {
					ft.cancel(true);
				}
			}
			if (fst != null) {
				if (!fst.isCancelled()) {
					fst.cancel(true);
				}
			}
			if (rt != null) {
				if (!rt.isCancelled()) {
					rt.cancel(true);
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
	public void onCreateOptionsMenu(Menu menu,
			com.actionbarsherlock.view.MenuInflater inflater) {
		inflater.inflate(R.menu.fragment_result_details, menu);

		if (item != null) {
			if (item.isReservable()) {
				menu.findItem(R.id.action_reservation).setVisible(true);
			} else {
				menu.findItem(R.id.action_reservation).setVisible(false);
			}
		} else {
			menu.findItem(R.id.action_reservation).setVisible(false);
		}

		String bib = app.getLibrary().getIdent();
		StarDataSource data = new StarDataSource(getOpacActivity());
		if ((id == null || id.equals("")) && item != null) {
			if (data.isStarredTitle(bib, title)) {
				menu.findItem(R.id.action_star).setIcon(
						R.drawable.ic_action_star_1);
			}
		} else {
			if (data.isStarred(bib, id)) {
				menu.findItem(R.id.action_star).setIcon(
						R.drawable.ic_action_star_1);
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		String bib = app.getLibrary().getIdent();
		if (item.getItemId() == R.id.action_reservation) {
			reservationStart();
			return true;
		} else if (item.getItemId() == android.R.id.home) {
			getActivity().finish(); // TODO: uhmm
			return true;
		} else if (item.getItemId() == R.id.action_export) {
			if (this.item == null) {
				Toast toast = Toast.makeText(getActivity(),
						getString(R.string.share_wait), Toast.LENGTH_SHORT);
				toast.show();
			} else {
				Intent intent = new Intent(android.content.Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

				// Add data to the intent, the receiving app will decide
				// what to do with it.
				intent.putExtra(Intent.EXTRA_SUBJECT, title);

				String t = title;
				try {
					bib = java.net.URLEncoder.encode(app.getLibrary()
							.getIdent(), "UTF-8");
					t = java.net.URLEncoder.encode(t, "UTF-8");
				} catch (UnsupportedEncodingException e) {
				}

				String text = title + "\n\n";

				for (Detail detail : this.item.getDetails()) {
					String colon = "";
					if (!detail.getDesc().endsWith(":"))
						colon = ":";
					text += detail.getDesc() + colon + "\n"
							+ detail.getContent() + "\n\n";
				}

				String shareUrl = app.getApi().getShareUrl(id, title);
				if (shareUrl != null)
					text += shareUrl;
				else
					text += "http://opacapp.de/:" + bib + ":" + id + ":" + t;

				intent.putExtra(Intent.EXTRA_TEXT, text);
				startActivity(Intent.createChooser(intent, getResources()
						.getString(R.string.share)));
			}
			return true;
		} else if (item.getItemId() == R.id.action_share) {
			if (this.item == null) {
				Toast toast = Toast.makeText(getActivity(),
						getString(R.string.share_wait), Toast.LENGTH_SHORT);
				toast.show();
			} else {
				Intent intent = new Intent(android.content.Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

				// Add data to the intent, the receiving app will decide
				// what to do with it.
				intent.putExtra(Intent.EXTRA_SUBJECT, title);

				String t = title;
				try {
					bib = java.net.URLEncoder.encode(app.getLibrary()
							.getIdent(), "UTF-8");
					t = java.net.URLEncoder.encode(t, "UTF-8");
				} catch (UnsupportedEncodingException e) {
				}

				String shareUrl = app.getApi().getShareUrl(id, title);
				if (shareUrl != null) {
					intent.putExtra(Intent.EXTRA_TEXT, shareUrl);
					startActivity(Intent.createChooser(intent, getResources()
							.getString(R.string.share)));
				} else {
					Toast toast = Toast.makeText(getActivity(),
							getString(R.string.share_notsupported),
							Toast.LENGTH_SHORT);
					toast.show();
				}
			}
			return true;
		} else if (item.getItemId() == R.id.action_star) {
			StarDataSource star = new StarDataSource(getOpacActivity());
			if (this.item == null) {
				Toast toast = Toast.makeText(getActivity(),
						getString(R.string.star_wait), Toast.LENGTH_SHORT);
				toast.show();
			} else if (id == null || id.equals("")) {
				if (star.isStarredTitle(bib, title)) {
					star.remove(star.getItemByTitle(bib, title));
					item.setIcon(R.drawable.ic_action_star_0);
				} else {
					star.star(null, title, bib);
					Toast toast = Toast.makeText(getActivity(),
							getString(R.string.starred), Toast.LENGTH_SHORT);
					toast.show();
					item.setIcon(R.drawable.ic_action_star_1);
				}
			} else {
				if (star.isStarred(bib, id)) {
					star.remove(star.getItem(bib, id));
					item.setIcon(R.drawable.ic_action_star_0);
				} else {
					star.star(id, title, bib);
					Toast toast = Toast.makeText(getActivity(),
							getString(R.string.starred), Toast.LENGTH_SHORT);
					toast.show();
					item.setIcon(R.drawable.ic_action_star_1);
				}
			}
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}
}
