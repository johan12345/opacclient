package de.geeksfactory.opacclient.frontend;

import org.holoeverywhere.widget.ProgressBar;
import org.json.JSONException;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.TextView;

import com.slidingmenu.lib.SlidingMenu;

import de.geeksfactory.opacclient.R;

public class InfoFragment extends OpacFragment {

	private WebView wvInfo;
	private View view;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		view = inflater.inflate(R.layout.info_fragment, container, false);

		load();

		SlidingMenu sm = ((ToplevelFragmentActivity) getActivity()).getSlidingMenu();
		if (sm.isEnabled())
			sm.setTouchModeAbove(SlidingMenu.TOUCHMODE_MARGIN);

		wvInfo = (WebView) view.findViewById(R.id.wvInfo);
		wvInfo.getSettings().setSupportZoom(true);
		wvInfo.setWebChromeClient(new WebChromeClient() {
			@Override
			public void onProgressChanged(WebView webView, int progress) {
				ProgressBar pbProgress = (ProgressBar) view
						.findViewById(R.id.pbWebProgress);
				if (pbProgress != null) {
					if (progress < 100
							&& pbProgress.getVisibility() == View.GONE) {
						pbProgress.setVisibility(View.VISIBLE);
					}
					pbProgress.setProgress(progress);
					if (progress == 100) {
						pbProgress.setVisibility(View.GONE);
					}
				}
			}
		});
		return view;
	}

	public void load() {
		wvInfo = (WebView) view.findViewById(R.id.wvInfo);
		TextView tvErr = (TextView) view.findViewById(R.id.tvErr);
		wvInfo.loadData(getString(R.string.loading), "text/html", null);
		try {
			String infoUrl = app.getLibrary().getData()
					.getString("information");
			if (infoUrl == null || infoUrl.equals("null")) {
				wvInfo.setVisibility(View.GONE);
				tvErr.setVisibility(View.VISIBLE);
				tvErr.setText(R.string.info_unsupported);
			} else if (infoUrl.startsWith("http")) {
				wvInfo.loadUrl(infoUrl);
			} else {
				wvInfo.loadUrl(app.getLibrary().getData().getString("baseurl")
						+ infoUrl);
			}
		} catch (JSONException e) {
			e.printStackTrace();
			wvInfo.setVisibility(View.GONE);
			tvErr.setVisibility(View.VISIBLE);
			tvErr.setText(R.string.info_error);
		}
	}

	public InfoFragment() {
		setHasOptionsMenu(true);
	}

	@Override
	public void accountSelected() {
		load();
	}
}
