/*
 * Copyright (c) 2011-2019, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.alg.feature.detect.chess;

import boofcv.abst.distort.FDistort;
import boofcv.alg.filter.blur.BlurImageOps;
import boofcv.gui.RenderCalibrationTargetsGraphics2D;
import boofcv.struct.image.GrayF32;
import georegression.metric.UtilAngle;
import org.ddogleg.struct.FastQueue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDetectChessboardCornersPyramid extends CommonChessboardCorners {

	/**
	 * Test everything together with perfect input, but rotate the chessboard
	 */
	@Test
	void process_rotate() {
		// make it bigger so that being a pyramid matters
		this.w = 50;

		RenderCalibrationTargetsGraphics2D renderer = new RenderCalibrationTargetsGraphics2D(p,1);
		renderer.chessboard(rows,cols,w);

		GrayF32 original = renderer.getGrayF32();
		GrayF32 rotated = original.createSameShape();

		DetectChessboardCornersPyramid<GrayF32,GrayF32> alg = new DetectChessboardCornersPyramid<>(GrayF32.class);
		alg.setPyramidTopSize(50);

		for (int i = 0; i < 10; i++) {
			angle = i*Math.PI/10;
			new FDistort(original,rotated).rotate(angle).apply();
			alg.process(rotated);
			checkSolution(rotated, alg);
		}
	}

	/**
	 * Apply heavy blurring to the input image so that the bottom most layer won't reliably detect corners
	 */
	@Test
	void process_blurred() {
		// make it bigger so that being a pyramid matters
		this.w = 50;

		RenderCalibrationTargetsGraphics2D renderer = new RenderCalibrationTargetsGraphics2D(p,1);
		renderer.chessboard(rows,cols,w);

		GrayF32 original = renderer.getGrayF32();
		GrayF32 blurred = original.createSameShape();

		DetectChessboardCornersPyramid<GrayF32,GrayF32> alg = new DetectChessboardCornersPyramid<>(GrayF32.class);
		alg.setPyramidTopSize(50);

		// mean blur messes it up much more than gaussian. This won't work if no pyramid
		BlurImageOps.mean(original,blurred,5,null,null);

		alg.process(blurred);
		checkSolution(blurred, alg);
	}

	@Test
	void constructPyramid() {
		DetectChessboardCornersPyramid<GrayF32,GrayF32> alg = new DetectChessboardCornersPyramid<>(GrayF32.class);

		// zero is no pyramid, full resolution only
		alg.setPyramidTopSize(0);

		alg.constructPyramid(new GrayF32(500,400));
		assertEquals(1,alg.pyramid.size());
		assertEquals(500,alg.pyramid.get(0).width);
		assertEquals(400,alg.pyramid.get(0).height);

		// now it should create a pyramid
		alg.setPyramidTopSize(100);
		alg.constructPyramid(new GrayF32(500,400));
		assertEquals(3,alg.pyramid.size());
		int divisor = 1;
		for (int level = 0; level < 3; level++) {
			assertEquals(500/divisor,alg.pyramid.get(level).width);
			assertEquals(400/divisor,alg.pyramid.get(level).height);
			divisor *= 2;
		}
	}

	private void checkSolution( GrayF32 input, DetectChessboardCornersPyramid<GrayF32, GrayF32> alg) {
//		System.out.println("------- ENTER");

		alg.process(input);
		FastQueue<ChessboardCorner> found = alg.getCorners();
		List<ChessboardCorner> expected = createExpected(rows,cols, input.width, input.height);

		assertEquals(expected.size(),found.size);

//		for (int i = 0; i < found.size; i++) {
//			found.get(i).print();
//		}
//		System.out.println("-------");

		for( ChessboardCorner c : expected ) {
			int matches = 0;
			for (int i = 0; i < found.size; i++) {
				ChessboardCorner f = found.get(i);
				if( f.distance(c) < 1.5 ) {
					matches++;
					assertEquals(0.0,UtilAngle.distHalf(c.orientation,f.orientation), 0.2);
					assertTrue(f.intensity>0);
				}
			}
//			c.print();
			assertEquals(1,matches);
		}
	}
}