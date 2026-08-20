package com.liftpath.components

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.liftpath.R
import com.liftpath.helpers.BackupManager
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.RestoreCoordinator
import com.liftpath.models.BackupBundle

/**
 * Offered once per install, right after [RestoreCoordinator] detects that Android's OS-level
 * Auto Backup restored a snapshot onto a fresh install.
 *
 * The bundle travels in as a serialized argument rather than a constructor parameter —
 * [DialogFragment] must be reconstructable from a no-arg constructor after process death, and
 * [BackupBundle] isn't Parcelable.
 */
class RestorePromptDialogFragment : DialogFragment() {

    private val bundle: BackupBundle? by lazy {
        arguments?.getString(ARG_BUNDLE_JSON)?.let { BackupManager.parse(it).getOrNull() }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false
        val bundle = bundle
        val builder = DialogHelper.createBuilder(requireContext())
            .setTitle(getString(R.string.restore_prompt_title))
            .setNegativeButton(getString(R.string.restore_prompt_dismiss)) { _, _ -> decline() }

        return if (bundle == null) {
            builder.setMessage(getString(R.string.restore_prompt_message_fallback)).create()
        } else {
            builder
                .setMessage(getString(R.string.restore_prompt_message, BackupManager.describe(bundle)))
                .setPositiveButton(getString(R.string.restore_prompt_confirm)) { _, _ -> restore(bundle) }
                .create()
        }
    }

    override fun onStart() {
        super.onStart()
        // Matches DialogHelper.showWithTransparentWindow — without it the window's own square
        // background peeks out from behind the dialog's rounded corners.
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun restore(bundle: BackupBundle) {
        val activity = activity ?: return
        RestoreCoordinator.resolve(activity)
        BackupManager.restoreAndRestart(activity, bundle)
    }

    private fun decline() {
        context?.let { RestoreCoordinator.resolve(it) }
    }

    companion object {
        private const val ARG_BUNDLE_JSON = "bundle_json"

        fun newInstance(bundle: BackupBundle): RestorePromptDialogFragment =
            RestorePromptDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_BUNDLE_JSON, BackupManager.serialize(bundle))
                }
            }
    }
}
