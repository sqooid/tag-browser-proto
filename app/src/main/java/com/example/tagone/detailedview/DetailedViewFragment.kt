package com.example.tagone.detailedview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.MediaController
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.tagone.R
import com.example.tagone.databinding.DetailedViewFragmentBinding
import com.example.tagone.databinding.TagChipBinding
import com.example.tagone.util.DisplayModel
import com.google.android.flexbox.FlexboxLayout

class DetailedViewFragment : Fragment() {

    private lateinit var viewModel: DetailedViewViewModel
    private lateinit var binding: DetailedViewFragmentBinding
    private lateinit var windowManager: WindowManager
    private lateinit var post: DisplayModel

    /**
     * Attempted shared element animation stuff
     */
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        postponeEnterTransition()
//        sharedElementEnterTransition =
//            TransitionInflater.from(context).inflateTransition(R.transition.change_transform_transition)
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//        startPostponedEnterTransition()
//    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        /**
         * Getting args from navigation
         */
        val args = DetailedViewFragmentArgs.fromBundle(requireArguments())
        post = args.post
        val postNumber = args.postNumber // The position of the post in list being navigated from

        /**
         * Creating data binding
         */
        binding = DetailedViewFragmentBinding.inflate(inflater)
        binding.lifecycleOwner = this

        /**
         * Getting windowManager and setting imageView constraints
         */
        windowManager = context?.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val height = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val display = windowManager.defaultDisplay
            val size = Point()
            display.getSize(size)
            size.y
        } else {
            windowManager.currentWindowMetrics.bounds.height()
        }
//        binding.detailedImageView.layoutParams.height = height
//        binding.detailedVideoView.layoutParams.height = height
        binding.frameLayout.layoutParams.height = height

        /**
         * Creating viewModel
         */
        val activity = requireNotNull(this.activity)
        viewModel = ViewModelProvider(
            this,
            DetailedViewViewModel.ViewModelFactory(activity.application, post, postNumber)
        ).get(
            DetailedViewViewModel::class.java
        )

        /**
         * Binding live data favourites variable in view model to keep track of whether post is favourited
         */
        viewModel.watchFavouriteStatus()

        /**
         * Binding variables
         */
        binding.post = post
        binding.viewModel = viewModel

        /**
         * Generating xml for tags below post
         */
        generateTags(post, inflater)

        /**
         * Observers
         */
        // Observer for tags being clicked to start search using tag in tag search fragment
        viewModel.linkedTag.observe(viewLifecycleOwner, Observer {
            it?.let {
                this.findNavController().navigate(DetailedViewFragmentDirections.followTag(it))
                viewModel.doneSearchingLinkedTag()
            }
        })
        // Favourite state observer. For favourite button
        val button = binding.favouriteButton
        viewModel.isFavourited.observe(viewLifecycleOwner, Observer {
            if (it) {
                button.backgroundTintList =
                    ColorStateList.valueOf(resources.getColor(R.color.likeButtonBackground))
            } else {
                button.backgroundTintList =
                    ColorStateList.valueOf(resources.getColor(R.color.elevation2))
            }
        })
        // Observer for copying post source to clipboard
        viewModel.sourceClip.observe(viewLifecycleOwner, Observer {
            it?.let {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Post source",it)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this.context, "Source copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        })

        setHasOptionsMenu(true)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        /**
         * Adding appropriate view (image or video)
         */
        when (post.fileExt) {
            "mp4", "webm" -> initializeVideo()
            else -> initializeImage()
        }
    }

    /**
     * Function run in the case that the media is an image or gif
     */
    private fun initializeImage() {
        binding.detailedVideoView.visibility = View.GONE
    }

    /**
     * Function run in the case that the media is a video
     */
    private fun initializeVideo() {
        // Removing imageView
        binding.detailedImageView.visibility = View.GONE
        // Showing indeterminate progress bar while loading
        val progressBar = binding.progressBar
        progressBar.visibility = View.VISIBLE
        val mediaController = MediaController(context, false)
        val videoView = binding.detailedVideoView
        viewModel.setVideoViewData(videoView, progressBar)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)
    }

    private fun generateTags(post: DisplayModel, inflater: LayoutInflater) {
        addTagCards(post.tagStringCharacter.split(" "), binding.tags.characterSegment, inflater)
        addTagCards(post.tagStringArtist.split(" "), binding.tags.artistSegment, inflater)
        addTagCards(post.tagStringCopyright.split(" "), binding.tags.copyrightSegment, inflater)
        addTagCards(post.tagStringGeneral.split(" "), binding.tags.generalSegment, inflater)
        addTagCards(post.tagStringMeta.split(" "), binding.tags.metaSegment, inflater)
    }

    private fun addTagCards(tags: List<String>, view: FlexboxLayout, inflater: LayoutInflater) {
        if (tags[0] == "") {
            val emptyView = inflater.inflate(R.layout.no_tags_placeholder, view, false)
            view.addView(emptyView)
        } else {
            for (tag in tags) {
                val binding = TagChipBinding.inflate(inflater)
                binding.tagChipTextView.text = tag
                binding.tagChipContainer.setOnClickListener {
                    viewModel.searchLinkedTag(tag)
                }
                view.addView(binding.root)
            }
        }
    }
}