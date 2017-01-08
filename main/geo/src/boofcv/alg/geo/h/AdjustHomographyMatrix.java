/*
 * Copyright (c) 2011-2017, Peter Abeles. All Rights Reserved.
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

package boofcv.alg.geo.h;

import boofcv.struct.geo.AssociatedPair;
import boofcv.struct.geo.PairLineNorm;
import georegression.geometry.GeometryMath_F64;
import org.ejml.alg.dense.decomposition.svd.SafeSvd_R64;
import org.ejml.data.RowMatrix_F64;
import org.ejml.factory.DecompositionFactory_R64;
import org.ejml.interfaces.decomposition.SingularValueDecomposition_F64;
import org.ejml.ops.CommonOps_R64;

import java.util.Arrays;

/**
 * The scale and sign of a homography matrix is ambiguous.  This contains functions which pick a reasonable scale
 * and the correct sign.  The second smallest singular value is set to one and the sign is chosen such that
 * the basic properties work.
 *
 * @author Peter Abeles
 */
public class AdjustHomographyMatrix {

	protected SingularValueDecomposition_F64<RowMatrix_F64> svd = new SafeSvd_R64(DecompositionFactory_R64.svd(0, 0, true, true, false));

	RowMatrix_F64 H_t = new RowMatrix_F64(3,3);

	public boolean adjust( RowMatrix_F64 H , AssociatedPair p ) {
		if( !findScaleH(H) )
			return false;

		adjustHomographSign(p,H);

		return true;
	}

	public boolean adjust( RowMatrix_F64 H , PairLineNorm p ) {
		if( !findScaleH(H) )
			return false;

		adjustHomographSign(p,H);

		return true;
	}

	/**
	 * The scale of H is found by computing the second smallest singular value.
	 */
	protected boolean findScaleH( RowMatrix_F64 H ) {
		if( !svd.decompose(H) )
			return false;

		Arrays.sort(svd.getSingularValues(), 0, 3);

		double scale = svd.getSingularValues()[1];
		CommonOps_R64.divide(H,scale);

		return true;
	}

	/**
	 * Since the sign of the homography is ambiguous a point is required to make sure the correct
	 * one was selected.
	 *
	 * @param p test point, used to determine the sign of the matrix.
	 */
	protected void adjustHomographSign( AssociatedPair p , RowMatrix_F64 H ) {
		double val = GeometryMath_F64.innerProd(p.p2, H, p.p1);

		if( val < 0 )
			CommonOps_R64.scale(-1, H);
	}

	/**
	 * Since the sign of the homography is ambiguous a point is required to make sure the correct
	 * one was selected.
	 *
	 * @param p test point, used to determine the sign of the matrix.
	 */
	protected void adjustHomographSign( PairLineNorm p , RowMatrix_F64 H ) {

		CommonOps_R64.transpose(H,H_t);

		double val = GeometryMath_F64.innerProd(p.l1, H_t, p.l2);

		if( val < 0 )
			CommonOps_R64.scale(-1, H);
	}
}
