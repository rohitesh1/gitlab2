package com.commit451.gitlab.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.design.widget.Snackbar
import android.support.design.widget.TextInputLayout
import android.support.v7.widget.Toolbar
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import com.afollestad.materialdialogs.color.ColorChooserDialog
import com.commit451.gitlab.App
import com.commit451.gitlab.R
import com.commit451.gitlab.extension.checkValid
import com.commit451.gitlab.model.api.Label
import com.commit451.gitlab.rx.CustomResponseSingleObserver
import com.commit451.gitlab.util.ColorUtil
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.parceler.Parcels
import retrofit2.HttpException
import retrofit2.Response
import timber.log.Timber

/**
 * Create a brand new label
 */
class AddNewLabelActivity : BaseActivity(), ColorChooserDialog.ColorCallback {

    companion object {

        private val KEY_PROJECT_ID = "project_id"

        val KEY_NEW_LABEL = "new_label"

        fun newIntent(context: Context, projectId: Long): Intent {
            val intent = Intent(context, AddNewLabelActivity::class.java)
            intent.putExtra(KEY_PROJECT_ID, projectId)
            return intent
        }
    }

    @BindView(R.id.root) lateinit var root: ViewGroup
    @BindView(R.id.toolbar) lateinit var toolbar: Toolbar
    @BindView(R.id.title_text_input_layout) lateinit var textInputLayoutTitle: TextInputLayout
    @BindView(R.id.description) lateinit var textDescription: TextView
    @BindView(R.id.image_color) lateinit var imageColor: ImageView
    @BindView(R.id.progress) lateinit var progress: View

    var chosenColor = -1

    @OnClick(R.id.root_color)
    fun onChooseColorClicked() {
        // Pass AppCompatActivity which implements ColorCallback, along with the textTitle of the dialog
        ColorChooserDialog.Builder(this, R.string.add_new_label_choose_color)
                .preselect(chosenColor)
                .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_new_label)
        ButterKnife.bind(this)

        toolbar.setNavigationIcon(R.drawable.ic_back_24dp)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        toolbar.inflateMenu(R.menu.create)
        toolbar.setOnMenuItemClickListener(Toolbar.OnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_create -> {
                    createLabel()
                    return@OnMenuItemClickListener true
                }
            }
            false
        })
    }

    override fun onColorSelection(dialog: ColorChooserDialog, @ColorInt selectedColor: Int) {
        chosenColor = selectedColor
        imageColor.setImageDrawable(ColorDrawable(selectedColor))
    }

    override fun onColorChooserDismissed(dialog: ColorChooserDialog) {
    }

    private val projectId: Long
        get() = intent.getLongExtra(KEY_PROJECT_ID, -1)

    private fun createLabel() {
        val valid = textInputLayoutTitle.checkValid()
        if (valid) {
            if (chosenColor == -1) {
                Snackbar.make(root, R.string.add_new_label_color_is_required, Snackbar.LENGTH_SHORT)
                        .show()
                return
            }
            val title = textInputLayoutTitle.editText!!.text.toString()
            var description: String? = null
            if (!TextUtils.isEmpty(textDescription.text)) {
                description = textDescription.text.toString()
            }
            var color: String? = null
            if (chosenColor != -1) {
                color = ColorUtil.convertColorIntToString(chosenColor)
                Timber.d("Setting color to %s", color)
            }
            progress.visibility = View.VISIBLE
            progress.alpha = 0.0f
            progress.animate().alpha(1.0f)
            App.get().gitLab.createLabel(projectId, title, color, description)
                    .compose(this.bindToLifecycle<Response<Label>>())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(object : CustomResponseSingleObserver<Label>() {

                        override fun error(e: Throwable) {
                            Timber.e(e)
                            progress.visibility = View.GONE
                            if (e is HttpException && e.response().code() == 409) {
                                Snackbar.make(root, R.string.label_already_exists, Snackbar.LENGTH_SHORT)
                                        .show()
                            } else {
                                Snackbar.make(root, R.string.failed_to_create_label, Snackbar.LENGTH_SHORT)
                                        .show()
                            }
                        }

                        override fun responseSuccess(label: Label) {
                            val data = Intent()
                            data.putExtra(KEY_NEW_LABEL, Parcels.wrap(label))
                            setResult(Activity.RESULT_OK, data)
                            finish()
                        }
                    })
        }
    }
}
