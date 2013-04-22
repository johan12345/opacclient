package de.geeksfactory.opacclient.frontend;

import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import de.geeksfactory.opacclient.R;

public class MetaAdapter extends ArrayAdapter<ContentValues> {

	private List<ContentValues> objects;
	private int spinneritem;

	@Override
	public View getDropDownView(int position, View contentView,
			ViewGroup viewGroup) {
		View view = null;

		if (objects.get(position) == null) {
			LayoutInflater layoutInflater = (LayoutInflater) getContext()
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = layoutInflater.inflate(
					R.layout.simple_spinner_dropdown_item, viewGroup, false);
			return view;
		}

		ContentValues item = objects.get(position);

		if (contentView == null) {
			LayoutInflater layoutInflater = (LayoutInflater) getContext()
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = layoutInflater.inflate(
					R.layout.simple_spinner_dropdown_item, viewGroup, false);
		} else {
			view = contentView;
		}

		TextView tvText = (TextView) view.findViewById(android.R.id.text1);
		tvText.setText(item.getAsString("value"));
		return view;
	}

	@Override
	public View getView(int position, View contentView, ViewGroup viewGroup) {
		View view = null;

		if (objects.get(position) == null) {
			LayoutInflater layoutInflater = (LayoutInflater) getContext()
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = layoutInflater.inflate(spinneritem, viewGroup, false);
			return view;
		}

		ContentValues item = objects.get(position);

		if (contentView == null) {
			LayoutInflater layoutInflater = (LayoutInflater) getContext()
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = layoutInflater.inflate(spinneritem, viewGroup, false);
		} else {
			view = contentView;
		}

		TextView tvText = (TextView) view.findViewById(android.R.id.text1);
		tvText.setText(item.getAsString("value"));
		return view;
	}

	public MetaAdapter(Context context, List<ContentValues> objects,
			int spinneritem) {
		super(context, R.layout.simple_spinner_item, objects);
		this.objects = objects;
		this.spinneritem = spinneritem;
	}

}