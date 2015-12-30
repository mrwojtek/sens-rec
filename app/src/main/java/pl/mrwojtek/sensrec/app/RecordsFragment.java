/*
 * (C) Copyright 2013, 2015 Wojciech Mruczkiewicz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Wojciech Mruczkiewicz
 */

package pl.mrwojtek.sensrec.app;

import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.android.supportv7.widget.decorator.DividerItemDecoration;

/**
 * Presents a list of the recorded files.
 */
public class RecordsFragment extends Fragment implements DeleteConfirmationDialog.OnDeleteListener {

    public static final String FRAGMENT_TAG = "RecordsFragment";

    private RecordsAdapter adapter;
    private RecyclerView recycler;
    private TextView fallbackText;

    private ActionMode actionMode;
    private ActionMode.Callback actionModeCallback = new ActionMode.Callback() {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            updateTitle(mode);
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.records_action_menu, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int itemId = item.getItemId();
            if (itemId == R.id.action_select_all) {
                adapter.activateAll(true);
                return true;
            } else if (itemId == R.id.action_share) {
                adapter.shareActivated();
                return true;
            } else if (itemId == R.id.action_delete) {
                getActivity().getSupportFragmentManager().beginTransaction()
                        .add(DeleteConfirmationDialog.newInstance(adapter.getActivatedCount(),
                                        FRAGMENT_TAG),
                                DeleteConfirmationDialog.DIALOG_TAG)
                        .commit();
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            adapter.activateAll(false);
            actionMode = null;
        }
    };

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Handler uiHandler = new Handler(getActivity().getMainLooper());
        adapter = new RecordsAdapter(this, uiHandler);
        adapter.onRestoreInstanceState(savedInstanceState);

        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);

        View view = inflater.inflate(R.layout.records_fragment, container, false);
        fallbackText = (TextView) view.findViewById(R.id.fallback_text);
        recycler = (RecyclerView) view.findViewById(R.id.records_recycler);
        recycler.addItemDecoration(new DividerItemDecoration(getActivity(),
                DividerItemDecoration.VERTICAL_LIST));
        recycler.setLayoutManager(new LinearLayoutManager(getActivity()));
        recycler.setItemAnimator(itemAnimator);
        recycler.setAdapter(adapter);
        recycler.setHasFixedSize(true);

        resolveVisibility();
        return view;
    }

    @Override
    public void onDestroyView() {
        adapter.onDestroy();
        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        adapter.onSaveInstanceState(outState);
    }

    public RecyclerView getRecycler() {
        return recycler;
    }

    public void resolveActionMode() {
        if (adapter.getActivatedCount() != 0) {
            if (actionMode == null) {
                AppCompatActivity activity = (AppCompatActivity) getActivity();
                actionMode = activity.startSupportActionMode(actionModeCallback);
            } else {
                updateTitle(actionMode);
            }
        } else {
            if (actionMode != null) {
                actionMode.finish();
            }
        }
    }

    @Override
    public void onDelete() {
        adapter.deleteActivated();
    }

    private void updateTitle(ActionMode actionMode) {
        actionMode.setTitle(getResources().getQuantityString(R.plurals.records_action_title,
                adapter.getActivatedCount(), adapter.getActivatedCount()));
    }

    public void resolveVisibility() {
        if (adapter.getItemCount() == 0) {
            fallbackText.setText(R.string.records_empty);
            fallbackText.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
        } else {
            fallbackText.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
        }
    }
}
