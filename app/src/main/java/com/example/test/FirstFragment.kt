package com.example.test

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.example.test.databinding.FragmentFirstBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonFirst.setOnClickListener {

//            val mFingerCaptureActivity = FingerCaptureActivity()
//            mFingerCaptureActivity.setPositionCodeIndex(0);
//            mFingerCaptureActivity.setUsername(username);
//            mFingerCaptureActivity.setLivenessCheck(livenessCheck);
//            mFingerCaptureActivity.setShowEllipses(showEllipses);
//            mFingerCaptureActivity.setCreateTemplates(createTemplates);
//            mFingerCaptureActivity.setOrientationCheck(orientationCheck);
//            mFingerCaptureActivity.setSaveSdkLogFlag(saveSdkLog);
//            mFingerCaptureActivity.setDetectorThreshold(detectorThreshold);
//
//            val intent = Intent(requireContext(), mFingerCaptureActivity::class.java)
//            startActivity(intent)
           findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    companion object {
        private const val username = "wamai"
        private const val livenessCheck = false
        private const val showEllipses = true
        private const val createTemplates = true
        private const val orientationCheck = false
        private const val saveSdkLog = false
        private const val detectorThreshold = 0.9f
    }
}