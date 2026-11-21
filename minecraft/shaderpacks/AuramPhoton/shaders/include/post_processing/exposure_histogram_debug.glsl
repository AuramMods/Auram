#ifndef INCLUDE_POST_PROCESSING_EXPOSURE_HISTOGRAM_DEBUG
#define INCLUDE_POST_PROCESSING_EXPOSURE_HISTOGRAM_DEBUG

// The chart compares each bin to an integer pixel row. Nine bits preserve
// every possible bar height, including a full-height 256-pixel bar.
const int histogram_debug_height = 256;
const int histogram_bar_bits = 9;
const uint histogram_bar_mask = (1u << histogram_bar_bits) - 1u;
const int histogram_bars_per_word = 3;
const int histogram_bars_per_vector = 4 * histogram_bars_per_word;
const int histogram_debug_vector_count
    = (HISTOGRAM_BINS + histogram_bars_per_vector - 1)
    / histogram_bars_per_vector;

#endif
