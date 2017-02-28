/*
 *     This file is part of ImageSURF.
 *
 *     ImageSURF is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ImageSURF is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with ImageSURF.  If not, see <http://www.gnu.org/licenses/>.
 */

package imagesurf.feature.calculator;

import java.io.Serializable;


/*
	Based on the ij.plugin.filter.RankFilters class.
 */
public class RankFilter implements Serializable
{
	static final long serialVersionUID = 42L;

	public enum Type {
		MEAN(0),MIN(1),MAX(2),MEDIAN(4);

		public final int value;

		Type(int value)
		{
			this.value = value;
		}
	};

	public RankFilter(Type filterType, double radius)
	{
		this.filterType = filterType;
		this.radius = radius;

		lineRadii = makeLineRadii(radius);
		kHeight = kHeight(lineRadii);
		kRadius = kRadius(lineRadii);
		kNPoints = kNPoints(lineRadii);

		smallKernel = kRadius < 2;
		minMaxOutliersSign = filterType == Type.MIN ? -1f : 1f;
	}

	public final Type filterType;
	public final double radius;
	private final int[] lineRadii;
	private final int kHeight;
	private final int kRadius;
	private final int kNPoints;
	private final boolean smallKernel;
	private final float minMaxOutliersSign;

	public byte[] rank(final byte[] pixels, final int width, final int height) {

		final int cacheWidth = width+2*kRadius;

		// 'cache' is the input buffer. Each line y in the image is mapped onto cache line y%cacheHeight
		final float[] cache = new float[cacheWidth*kHeight];

		final int xmin = 0 - kRadius;
		final int xmax = width + kRadius;
		final int[]kernel = makeKernel(lineRadii, cacheWidth);

		final int padLeft = xmin<0 ? -xmin : 0;
		final int padRight = xmax>width? xmax-width : 0;
		final int xminInside = xmin>0 ? xmin : 0;
		final int xmaxInside = xmax<width ? xmax : width;
		final int widthInside = xmaxInside - xminInside;

		final double[] sums = filterType == Type.MEAN ? new double[2] : null;
		final float[] medianAboveBuffer = (filterType == Type.MEDIAN) ? new float[kNPoints] : null;
		final float[] medianBelowBuffer = (filterType == Type.MEDIAN) ? new float[kNPoints] : null;

		final float[] values = new float[width];

		int previousY = kHeight /2- kHeight;

		for (int y=0;y< height;y++)
		{
			for (int i=0; i<kernel.length; i++)	//shift kernel pointers to new line
				kernel[i] = (kernel[i] + cacheWidth *(y-previousY))% cache.length;

			previousY = y;

			int yStartReading = y==0 ? Math.max(0- kHeight /2, 0) : y+ kHeight /2;
			for (int yNew = yStartReading; yNew<=y+ kHeight /2; yNew++)
			{ //only 1 line except at start
				readLineToCacheOrPad(pixels, width, height, xminInside, widthInside,
						cache, cacheWidth, padLeft, padRight, yNew);
			}

			int cacheLineP = cacheWidth * (y % kHeight) + kRadius;	//points to pixel (0, y)
			filterLine(values, width, cache, kernel, cacheLineP,	sums, medianAboveBuffer, medianBelowBuffer);
			writeLineToPixels(values, pixels, y*width, width);	// W R I T E
		}

		return pixels;
	}

	public short[] rank(final short[] pixels, final int width, final int height) {

		final int cacheWidth = width+2*kRadius;

		// 'cache' is the input buffer. Each line y in the image is mapped onto cache line y%cacheHeight
		final float[] cache = new float[cacheWidth*kHeight];

		final int xmin = 0 - kRadius;
		final int xmax = width + kRadius;
		final int[]kernel = makeKernel(lineRadii, cacheWidth);

		final int padLeft = xmin<0 ? -xmin : 0;
		final int padRight = xmax>width? xmax-width : 0;
		final int xminInside = xmin>0 ? xmin : 0;
		final int xmaxInside = xmax<width ? xmax : width;
		final int widthInside = xmaxInside - xminInside;

		final double[] sums = filterType == Type.MEAN ? new double[2] : null;
		final float[] medianAboveBuffer = (filterType == Type.MEDIAN) ? new float[kNPoints] : null;
		final float[] medianBelowBuffer = (filterType == Type.MEDIAN) ? new float[kNPoints] : null;

		final float[] values = new float[width];

		int previousY = kHeight /2- kHeight;

		for (int y=0;y< height;y++)
		{
			for (int i=0; i<kernel.length; i++)	//shift kernel pointers to new line
				kernel[i] = (kernel[i] + cacheWidth *(y-previousY))% cache.length;

			previousY = y;

			int yStartReading = y==0 ? Math.max(0- kHeight /2, 0) : y+ kHeight /2;
			for (int yNew = yStartReading; yNew<=y+ kHeight /2; yNew++)
			{ //only 1 line except at start
				readLineToCacheOrPad(pixels, width, height, xminInside, widthInside,
						cache, cacheWidth, padLeft, padRight, yNew);
			}

			int cacheLineP = cacheWidth * (y % kHeight) + kRadius;	//points to pixel (0, y)
			filterLine(values, width, cache, kernel, cacheLineP,	sums, medianAboveBuffer, medianBelowBuffer);
			writeLineToPixels(values, pixels, y*width, width);	// W R I T E
		}

		return pixels;
	}

	private void filterLine(final float[] values, final int width, final float[] cache, final int[] kernel, final int cacheLineP,
	                        final double[] sums, final float[] medianBuf1, final float[] medianBuf2) {

		float max = 0f;
		float median = Float.isNaN(cache[cacheLineP]) ? 0 : cache[cacheLineP];	// a first guess
		boolean fullCalculation = true;
		for (int x=0; x<width; x++) {							// x is with respect to 0
			if (fullCalculation)
			{
				fullCalculation = smallKernel;	//for small kernel, always use the full area, not incremental algorithm
				switch (filterType)
				{
					case MIN:
					case MAX:
						max = getAreaMax(cache, x, kernel, 0, -Float.MAX_VALUE, minMaxOutliersSign);
						values[x] = max*minMaxOutliersSign;

						continue;

					case MEAN:
						getAreaSums(cache, x, kernel, sums);
						break;
				}
			}
			else
			{
				switch (filterType)
				{
					case MIN:
					case MAX:
						final float newPointsMax = getSideMax(cache, x, kernel, true, minMaxOutliersSign);
						if (newPointsMax >= max) { //compare with previous maximum 'max'
							max = newPointsMax;
						} else {
							float removedPointsMax = getSideMax(cache, x, kernel, false, minMaxOutliersSign);
							if (removedPointsMax >= max)
								max = getAreaMax(cache, x, kernel, 1, newPointsMax, minMaxOutliersSign);
						}

						values[x] = max*minMaxOutliersSign;
						continue;

					case MEAN:
						addSideSums(cache, x, kernel, sums);
						if (Double.isNaN(sums[0])) //avoid perpetuating NaNs into remaining line
							fullCalculation = true;
						break;
				}
			}

			switch(filterType)
			{
				case MEAN:
					values[x] = (float)(sums[0]/kNPoints);
					break;
				case MEDIAN:
					median = getMedian(cache, x, kernel, medianBuf1, medianBuf2, kNPoints, median);
					values[x] = median;
					break;
			}
		}
	}

	/** Read a line into the cache (including padding in x).
	 *	If y>=height, instead of reading new data, it duplicates the line y=height-1.
	 *	If y==0, it also creates the data for y<0, as far as necessary, thus filling the cache with
	 *	more than one line (padding by duplicating the y=0 row).
	 */
	private void readLineToCacheOrPad(final Object pixels, final int width, final int height, final int xminInside, final int widthInside,
	                                         final float[] cache, final int cacheWidth, final int padLeft, final int padRight,
	                                         final int y) {
		final int lineInCache = y%kHeight;
		if (y < height) {

			if(pixels instanceof byte[])
				readLineToCache((byte[])pixels, y*width, xminInside, widthInside,
					cache, lineInCache*cacheWidth, padLeft, padRight);
			else if (pixels instanceof short[])
			readLineToCache((short[])pixels, y*width, xminInside, widthInside,
					cache, lineInCache*cacheWidth, padLeft, padRight);
			else
				throw new IllegalArgumentException("pixels argument must be byte or short array. pixels is "+pixels.getClass().getName());

			if (y==0) for (int prevY = 0-kHeight/2; prevY<0; prevY++) {	//for y<0, pad with y=0 border pixels
				int prevLineInCache = kHeight+prevY;
				System.arraycopy(cache, 0, cache, prevLineInCache*cacheWidth, cacheWidth);
			}
		} else
			System.arraycopy(cache, cacheWidth*((height-1)%kHeight), cache, lineInCache*cacheWidth, cacheWidth);
	}


	/** Read a line into the cache (includes conversion to flaot). Pad with edge pixels in x if necessary */
	private static void readLineToCache(final byte[] pixels, final int pixelLineP, final int xminInside, final int widthInside,
	                                    final float[] cache, final int cacheLineP, final int padLeft, final int padRight) {

		for (int pp=pixelLineP+xminInside, cp=cacheLineP+padLeft; pp<pixelLineP+xminInside+widthInside; pp++,cp++)
				cache[cp] = pixels[pp]&0xff;

		for (int cp=cacheLineP; cp<cacheLineP+padLeft; cp++)
			cache[cp] = cache[cacheLineP+padLeft];
		for (int cp=cacheLineP+padLeft+widthInside; cp<cacheLineP+padLeft+widthInside+padRight; cp++)
			cache[cp] = cache[cacheLineP+padLeft+widthInside-1];
	}

	private static void readLineToCache(final short[] pixels, final int pixelLineP, final int xminInside, final int widthInside,
										final float[] cache, final int cacheLineP, final int padLeft, final int padRight) {

		for (int pp=pixelLineP+xminInside, cp=cacheLineP+padLeft; pp<pixelLineP+xminInside+widthInside; pp++,cp++)
			cache[cp] = pixels[pp]&0xffff;

		for (int cp=cacheLineP; cp<cacheLineP+padLeft; cp++)
			cache[cp] = cache[cacheLineP+padLeft];
		for (int cp=cacheLineP+padLeft+widthInside; cp<cacheLineP+padLeft+widthInside+padRight; cp++)
			cache[cp] = cache[cacheLineP+padLeft+widthInside-1];
	}

	/** Write a line to pixels arrax, converting from float (not for float data!)
	 *	No checking for overflow/underflow
	 */
	private static void writeLineToPixels(final float[] values, final byte[] pixels, int pixelP, final int length) {
			for (int i=0; i<length; i++,pixelP++)
				pixels[pixelP] = (byte)(((int)(values[i] + 0.5f))&0xff);
	}

	private static void writeLineToPixels(final float[] values, final short[] pixels, int pixelP, final int length) {
		for (int i=0; i<length; i++,pixelP++)
			pixels[pixelP] = (short)(((int)(values[i] + 0.5f))&0xffff);
	}

	/** Get max (or -min if sign=-1) within the kernel area.
	 *	@param ignoreRight should be 0 for analyzing all data or 1 for leaving out the row at the right
	 *	@param max should be -Float.MAX_VALUE or the smallest value the maximum can be */
	private static float getAreaMax(final float[] cache, final int xCache0, final int[] kernel, final int ignoreRight, float max, final float sign) {
		for (int kk=0; kk<kernel.length; kk++) {	// y within the cache stripe (we have 2 kernel pointers per cache line)
			for (int p=kernel[kk++]+xCache0; p<=kernel[kk]+xCache0-ignoreRight; p++) {
				final float v = cache[p]*sign;
				if (max < v) max = v;
			}
		}
		return max;
	}

	/** Get max (or -min if sign=-1) at the right border inside or left border outside the kernel area.
	 *	x between 0 and cacheWidth-1 */
	private static float getSideMax(final float[] cache, int xCache0, final int[] kernel, final boolean isRight, final float sign) {
		float max = -Float.MAX_VALUE;
		if (!isRight) xCache0--;
		for (int kk= isRight ? 1 : 0; kk<kernel.length; kk+=2) {	// y within the cache stripe (we have 2 kernel pointers per cache line)
			final float v = cache[xCache0 + kernel[kk]]*sign;
			if (max < v) max = v;
		}
		return max;
	}

	/** Get sum of values and values squared within the kernel area.
	 *	x between 0 and cacheWidth-1
	 *	Output is written to array sums[0] = sum; sums[1] = sum of squares */
	private static void getAreaSums(final float[] cache, int xCache0, final int[] kernel, final double[] sums) {
		double sum=0, sum2=0;
		for (int kk=0; kk<kernel.length; kk++) {	// y within the cache stripe (we have 2 kernel pointers per cache line)
			for (int p=kernel[kk++]+xCache0; p<=kernel[kk]+xCache0; p++) {
				final float v = cache[p];
				sum += v;
				sum2 += v*v;
			}
		}
		sums[0] = sum;
		sums[1] = sum2;
		return;
	}

	/** Add all values and values squared at the right border inside minus at the left border outside the kernal area.
	 *	Output is added or subtracted to/from array sums[0] += sum; sums[1] += sum of squares  when at
	 *	the right border, minus when at the left border */
	private static void addSideSums(final float[] cache, final int xCache0, final int[] kernel, final double[] sums) {
		double sum=0, sum2=0;
		for (int kk=0; kk<kernel.length; /*k++;k++ below*/) {
			float v = cache[kernel[kk++]+(xCache0-1)];
			sum -= v;
			sum2 -= v*v;
			v = cache[kernel[kk++]+xCache0];
			sum += v;
			sum2 += v*v;
		}
		sums[0] += sum;
		sums[1] += sum2;
		return;
	}

	/** Get median of values within kernel-sized neighborhood. Kernel size kNPoints should be odd.
	 */
	private static float getMedian(final float[] cache, final int xCache0, final int[] kernel,
	                               final float[] aboveBuf, final float[]belowBuf, final int kNPoints, final float guess) {
		int nAbove = 0, nBelow = 0;
		for (int kk=0; kk<kernel.length; kk++) {
			for (int p=kernel[kk++]+xCache0; p<=kernel[kk]+xCache0; p++) {
				float v = cache[p];
				if (v > guess) {
					aboveBuf[nAbove] = v;
					nAbove++;
				}
				else if (v < guess) {
					belowBuf[nBelow] = v;
					nBelow++;
				}
			}
		}
		int half = kNPoints/2;
		if (nAbove>half)
			return findNthLowestNumber(aboveBuf, nAbove, nAbove-half-1);
		else if (nBelow>half)
			return findNthLowestNumber(belowBuf, nBelow, half);
		else
			return guess;
	}

	/** Find the n-th lowest number in part of an array
	 *	@param buf The input array. Only values 0 ... bufLength are read. <code>buf</code> will be modified.
	 *	@param bufLength Number of values in <code>buf</code> that should be read
	 *	@param n which value should be found; n=0 for the lowest, n=bufLength-1 for the highest
	 *	@return the value */
	private static float findNthLowestNumber(final float[] buf, final int bufLength, final int n) {
		// Hoare's find, algorithm, based on http://www.geocities.com/zabrodskyvlada/3alg.html
		// Contributed by Heinz Klar
		int i,j;
		int l=0;
		int m=bufLength-1;
		float med=buf[n];
		float dum ;

		while (l<m) {
			i=l ;
			j=m ;
			do {
				while (buf[i]<med) i++ ;
				while (med<buf[j]) j-- ;
				dum=buf[j];
				buf[j]=buf[i];
				buf[i]=dum;
				i++ ; j-- ;
			} while ((j>=n) && (i<=n)) ;
			if (j<n) l=i ;
			if (n<i) m=j ;
			med=buf[n] ;
		}
		return med ;
	}

	/** Create a circular kernel (structuring element) of a given radius.
	 *	@param radius
	 *	Radius = 0.5 includes the 4 neighbors of the pixel in the center,
	 *	radius = 1 corresponds to a 3x3 kernel size.
	 *	@return the circular kernel
	 *	The output is an array that gives the length of each line of the structuring element
	 *	(kernel) to the left (negative) and to the right (positive):
	 *	[0] left in line 0, [1] right in line 0,
	 *	[2] left in line 2, ...
	 *	The maximum (absolute) value should be kernelRadius.
	 *	Array elements at the end:
	 *	length-2: nPoints, number of pixels in the kernel area
	 *	length-1: kernelRadius in x direction (kernel width is 2*kernelRadius+1)
	 *	Kernel height can be calculated as (array length - 1)/2 (odd number);
	 *	Kernel radius in y direction is kernel height/2 (truncating integer division).
	 *	Note that kernel width and height are the same for the circular kernels used here,
	 *	but treated separately for the case of future extensions with non-circular kernels.
	 */
	private static int[] makeLineRadii(final double radius) {

		final int r2 = (int) (radius*radius) + 1;
		final int kRadius = (int)(Math.sqrt(r2+1e-10));
		final int kHeight = 2*kRadius + 1;
		final int[] kernel = new int[2*kHeight + 2];
		kernel[2*kRadius]	= -kRadius;
		kernel[2*kRadius+1] =  kRadius;
		int nPoints = 2*kRadius+1;
		for (int y=1; y<=kRadius; y++) {		//lines above and below center together
			int dx = (int)(Math.sqrt(r2-y*y+1e-10));
			kernel[2*(kRadius-y)]	= -dx;
			kernel[2*(kRadius-y)+1] =  dx;
			kernel[2*(kRadius+y)]	= -dx;
			kernel[2*(kRadius+y)+1] =  dx;
			nPoints += 4*dx+2;	//2*dx+1 for each line, above&below
		}
		kernel[kernel.length-2] = nPoints;
		kernel[kernel.length-1] = kRadius;

		return kernel;
	}

	//kernel height
	private static int kHeight(final int[] lineRadii) {
		return (lineRadii.length-2)/2;
	}

	//kernel radius in x direction. width is 2+kRadius+1
	private static int kRadius(final int[] lineRadii) {
		return lineRadii[lineRadii.length-1];
	}

	//number of points in kernal area
	private static int kNPoints(final int[] lineRadii) {
		return lineRadii[lineRadii.length-2];
	}

	//cache pointers for a given kernel
	private static int[] makeKernel(final int[] lineRadii, final int cacheWidth) {
		int kRadius = kRadius(lineRadii);
		int kHeight = kHeight(lineRadii);
		int[] cachePointers = new int[2*kHeight];
		for (int i=0; i<kHeight; i++) {
			cachePointers[2*i]	 = i*cacheWidth+kRadius + lineRadii[2*i];
			cachePointers[2*i+1] = i*cacheWidth+kRadius + lineRadii[2*i+1];
		}
		return cachePointers;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		RankFilter that = (RankFilter) o;

		if (Double.compare(that.radius, radius) != 0) return false;
		if (filterType != that.filterType) return false;

		return true;
	}

	@Override
	public int hashCode()
	{
		int result;
		long temp;
		result = filterType.hashCode();
		temp = Double.doubleToLongBits(radius);
		result = 31 * result + (int) (temp ^ (temp >>> 32));
		return result;
	}
}