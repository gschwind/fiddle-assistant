/*

Copyright (2020-2021) Benoit Gschwind <gschwind@gnu-log.net>

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
	std::array<T, g_fft_n/2> spectrum;

	std::vector<int> max_args;
	std::vector<int> mask;

	double freq_factor;
	int sample_length;
	int _sample_rate;

	double min_volume;
	double cur_volume;

	double min_volume_sensitivity;

	static inline constexpr float _PI() { return 3.14159265358979323846; }

	// not-normed gaussian.
	static inline constexpr float _gauss(float x, float sigma)
	{
	    return std::exp(-(x*x)/(2*(sigma*sigma)));
	}

	/**
	 * Compute if _n_ is divisible by _d_ with a tolerence _t_
	 **/
	static inline bool is_divisible(double n, double d, double t) {
		double p = std::round(n/d);
		return std::fabs(p*d-n)<t;
	}

	tone_handler () {
		max_args.reserve(50); // avoid useless realloc.
		mask.reserve(50);

		min_volume = 1.0e10;
		cur_volume = 0.0;

		min_volume_sensitivity = 5.0;

	}

	void set_min_volume_sensitivity(double sensitivity) {
        min_volume_sensitivity = sensitivity;
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
		double sigma = 1.0/(2.0*_PI()*40.0);
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

		for (int i = 2; i < (g_fft_n/2-1); ++i) {
			if (bgn[i] < max*0.10)
				continue;
			if (bgn[i-1] > bgn[i])
				continue;
			if (bgn[i+1] > bgn[i])
				continue;

			max_args.push_back(i);

			// Noise generate a lot of peaks, valid sound genera less than ~20 significative peaks
			if (max_args.size() >= 20) {
				return std::nan("");
			}

		}

		// Only 0 in the list, should never happen, at less ma is in the list
		if (max_args.size() < 1)
			return std::nan("");

		// Only one peak found, return this value.
		if (max_args.size() == 1)
			return ref_freq*freq_factor;


		int max_divide = -1;
		int divide = 1;
		for (int d = 1; d < 8; ++d) {

			int ndiv = 0;
			for (auto x: max_args) {
				if (is_divisible(x, ref_freq/d, 15*freq_factor) and x > 50*freq_factor) {
					ndiv += 1;
				}
			}

			if (ndiv > max_divide) {
				max_divide = ndiv;
				divide = d;
			}
		}

		// Debug stuff
		mask.resize(max_args.size());
		if (max_divide > 0) {
			for (int i = 0; i < max_args.size(); ++i) {
				mask[i] = is_divisible(max_args[i], ref_freq/divide, 15*freq_factor) and max_args[i] > 50*freq_factor;
			}
		}

		if (max_divide < 1) {
			return ref_freq*freq_factor;
		} else {
			return ref_freq*freq_factor/divide;
		}

	}


	template<typename TX>
	double compute_freq(TX const * bgn, TX const * end)
	{
		TX const * srt = std::max(bgn, end-sample_length);

		min_volume += 1.0e-4;

		cur_volume = 0.0;
		for (auto x = srt; x < end; ++x) {
			cur_volume += std::fabs(*x);
        }

		if (cur_volume < min_volume) {
			min_volume -= 0.5*(min_volume-cur_volume);
		}

		if (cur_volume < min_volume * min_volume_sensitivity)
			return std::nan("");

		// reverse the signal, ensuring the analysis occure to last aquired data.
		for(int i = 0; srt < end; ++srt, ++i) {
            g_fft_ibuffer[i] = std::complex<T>{static_cast<T>(*srt) * gaussian_filter[i]};
        }

		g_fft_plan.transform(&g_fft_ibuffer[0], &g_fft_obuffer[0]);

		for (int i = 1; i < g_fft_n/2; ++i) {
			spectrum[i] = std::abs(g_fft_obuffer[i]);
		}

		return find_frequency(spectrum.begin(), spectrum.end());
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
