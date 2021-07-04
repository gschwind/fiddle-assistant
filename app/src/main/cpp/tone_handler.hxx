/*

Copyright (2020) Benoit Gschwind <gschwind@gnu-log.net>

This file is part of fiddle-assistant.

fiddle-assistant is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

fiddle-assistant is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with fiddle-assistant.  If not, see <https://www.gnu.org/licenses/>.

 */

#ifndef SRC_TONE_HANDLER_HXX_
#define SRC_TONE_HANDLER_HXX_

#include <array>
#include <algorithm>
#include <vector>

#include "kissfft.hh"


template<typename T, std::size_t g_fft_n>
struct tone_handler {

	std::array<typename kissfft<T>::cpx_t, g_fft_n> g_fft_ibuffer{kissfft<float>::cpx_t{}};
	std::array<typename kissfft<T>::cpx_t, g_fft_n> g_fft_obuffer{kissfft<float>::cpx_t{}};
	kissfft<T> g_fft_plan{g_fft_n, false};
	std::array<T, g_fft_n> gaussian_filter;
	std::array<T, g_fft_n> spectrum;

	std::vector<int> max_args;
	std::vector<double> diff;

	double freq_factor;
	int sample_length;
	int _sample_rate;

	static inline constexpr float _PI() { return std::atan(1.0)*4.0; }

	// not-normed gaussian.
	static inline constexpr float _gauss(float x, float sigma)
	{
	    return std::exp(-(x*x)/(2*(sigma*sigma)));
	}

	tone_handler () {

	}

	int init_sample_rate(int sample_rate) {
		// I want convolve my fourier transform with 20 Hz sigma.
		// This mean omega is 2*pi*20, that mean sigma in time space must be 1.0/(2*pi*20)
		// Thus to have a good gausian I need at less 3 sigma at both side of the center of the gaussian.
		// Let'sgo for 4*sigma in both side
		// The sample length should be:

		_sample_rate = sample_rate;
		freq_factor = static_cast<double>(sample_rate)/static_cast<double>(g_fft_n);

		double time_delta = 1.0/sample_rate;
		double sigma = 1.0/(2.0*_PI()*20.0);
		// sample_length = 2.0*4.0*sigma/time_delta; that can be simplified as follow
		sample_length = 6.0*sample_rate*sigma+1;

		if (sample_length > g_fft_n)
		    return -1;

		double sum_fix = 0.0;
		for (int i = 0; i < sample_length; ++i) {
			sum_fix += gaussian_filter[i] = _gauss(time_delta*(i-static_cast<int>(sample_length/2)), sigma);
		}

		for (int i = 0; i < sample_length; ++i) {
			gaussian_filter[i] /= sum_fix;
		}

		return 0;

	}

	double find_frequency(T * bgn, T * end)
	{

		auto max_elem = std::max_element(bgn+2, end);
		double ref_freq = std::distance(bgn, max_elem);
		float max = *max_elem;

		max_args.resize(0); // remove all elements

		for (int i = 150.0/freq_factor; i < (g_fft_n/2-1); ++i) {
			if (bgn[i] < max*0.07)
				continue;
			if (bgn[i-1] > bgn[i])
				continue;
			if (bgn[i+1] > bgn[i])
				continue;

			//Keeponly the 15 higest peak
			max_args.insert(std::upper_bound(max_args.begin(), max_args.end(), i,
											 [bgn](int a, int b) -> bool { return bgn[a] > bgn[b]; }), i);
			if (max_args.size() > 5) {
				max_args.resize(5);
			}

		}

		max_args.push_back(0);
		std::sort(max_args.begin(), max_args.end());

		if (max_args.size() == 1) {
			double f = max_args[1]*freq_factor;
			// only one peaks is detected is high
			// other wise it's noise
			return f>440.0?f:std::nan("");
		} else if (max_args.size() < 4) {
			// If few peak the first one or the max one should be ok.
			double f = max_args[1]*freq_factor;
			return f>220.0?f:std::nan("");
		} else {
			diff.resize(max_args.size() - 1);
			for (int i = 1; i < max_args.size(); ++i) {
				diff[i - 1] = max_args[i] - max_args[i - 1];
			}

			std::sort(diff.begin(), diff.end());

			for (auto &x: diff) {
				double f = x * freq_factor;
				if (f > 150.0)
					return f;
			}

			return std::nan("");
		}

	}


	template<typename TX>
	double compute_freq(TX * data, std::size_t len)
	{
		len = std::min<std::size_t>(len, sample_length);

		// reverse the signal, ensuring the analysis occure to last aquired data.
		TX * end = &data[len-1];
		for (int i = 0; i < len; ++i, --end) {
            g_fft_ibuffer[i] = std::complex<T>((*end) * gaussian_filter[i], 0.0f);
        }

		g_fft_plan.transform(&g_fft_ibuffer[0], &g_fft_obuffer[0]);

		for (int i = 1; i < g_fft_n/2; ++i) {
			spectrum[i] = std::abs(g_fft_obuffer[i]);
		}

		return find_frequency(&spectrum[0], &spectrum[g_fft_n/2]);
	}

	template<typename TX>
	double absolute_volume(TX * data, std::size_t len) {
	    double sum = 0.0;
	    for (int i = 0; i < len; ++i) {
	        sum += data[i]*data[i];
	    }
	    return sum/_sample_rate;
	}

};

#endif /* SRC_TONE_HANDLER_HXX_ */
