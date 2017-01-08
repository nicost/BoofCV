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

package boofcv.alg.geo.f;

import boofcv.alg.geo.LowLevelMultiViewOps;
import boofcv.struct.geo.AssociatedPair;
import georegression.struct.point.Point2D_F64;
import org.ejml.data.RowMatrix_F64;
import org.ejml.factory.DecompositionFactory_R64;
import org.ejml.interfaces.decomposition.SingularValueDecomposition_F64;
import org.ejml.ops.CommonOps_R64;
import org.ejml.ops.SingularOps_R64;

import java.util.List;

/**
 * <p>
 * Base class for linear algebra based algorithms for computing the Fundamental/Essential matrices.
 * </p>
 *
 * <p>
 * The computed fundamental matrix follow the following convention (with no noise) for the associated pair:
 * x2<sup>T</sup>*F*x1 = 0<br>
 * x1 = keyLoc and x2 = currLoc.
 * </p>
 *
 * @author Peter Abeles
 */
public abstract class FundamentalLinear {

	// contains the set of equations that are solved
	protected RowMatrix_F64 A = new RowMatrix_F64(1,9);
	// svd used to extract the null space
	protected SingularValueDecomposition_F64<RowMatrix_F64> svdNull = DecompositionFactory_R64.svd(9, 9, false, true, false);
	// svd used to enforce constraings on 3x3 matrix
	protected SingularValueDecomposition_F64<RowMatrix_F64> svdConstraints = DecompositionFactory_R64.svd(3, 3, true, true, false);

	// SVD decomposition of F = U*S*V^T
	protected RowMatrix_F64 svdU;
	protected RowMatrix_F64 svdS;
	protected RowMatrix_F64 svdV;

	protected RowMatrix_F64 temp0 = new RowMatrix_F64(3,3);

	// matrix used to normalize results
	protected RowMatrix_F64 N1 = new RowMatrix_F64(3,3);
	protected RowMatrix_F64 N2 = new RowMatrix_F64(3,3);

	// should it compute a fundamental (true) or essential (false) matrix?
	boolean computeFundamental;

	/**
	 * Specifies which type of matrix is to be computed
	 *
	 * @param computeFundamental true it computes a fundamental matrix and false for essential
	 */
	public FundamentalLinear( boolean computeFundamental ) {
		this.computeFundamental = computeFundamental;
	}

	/**
	 * Projects the found estimate of E onto essential space.
	 *
	 * @return true if svd returned true.
	 */
	protected boolean projectOntoEssential( RowMatrix_F64 E ) {
		if( !svdConstraints.decompose(E) ) {
			return false;
		}
		svdV = svdConstraints.getV(svdV,false);
		svdU = svdConstraints.getU(svdU,false);
		svdS = svdConstraints.getW(svdS);

		SingularOps_R64.descendingOrder(svdU, false, svdS, svdV, false);

		// project it into essential space
		// the scale factor is arbitrary, but the first two singular values need
		// to be the same.  so just set them to one
		svdS.unsafe_set(0, 0, 1);
		svdS.unsafe_set(1, 1, 1);
		svdS.unsafe_set(2, 2, 0);

		// recompute F
		CommonOps_R64.mult(svdU, svdS, temp0);
		CommonOps_R64.multTransB(temp0,svdV, E);

		return true;
	}

	/**
	 * Projects the found estimate of F onto Fundamental space.
	 *
	 * @return true if svd returned true.
	 */
	protected boolean projectOntoFundamentalSpace( RowMatrix_F64 F ) {
		if( !svdConstraints.decompose(F) ) {
			return false;
		}
		svdV = svdConstraints.getV(svdV,false);
		svdU = svdConstraints.getU(svdU,false);
		svdS = svdConstraints.getW(svdS);

		SingularOps_R64.descendingOrder(svdU, false, svdS, svdV, false);

		// the smallest singular value needs to be set to zero, unlike
		svdS.set(2, 2, 0);

		// recompute F
		CommonOps_R64.mult(svdU, svdS, temp0);
		CommonOps_R64.multTransB(temp0,svdV, F);

		return true;
	}

	/**
	 * Undo the normalization done to the input matrices for a Fundamental matrix.
	 * <br>
	 * M = N<sub>2</sub><sup>T</sup>*M*N<sub>1</sub>
	 *
	 * @param M Either the homography or fundamental matrix computed from normalized points.
	 * @param N1 normalization matrix.
	 * @param N2 normalization matrix.
	 */
	protected void undoNormalizationF(RowMatrix_F64 M, RowMatrix_F64 N1, RowMatrix_F64 N2) {
		// M = N2^T * M * N1
		CommonOps_R64.multTransA(N2,M,temp0);
		CommonOps_R64.mult(temp0,N1,M);
	}

	/**
	 * Reorganizes the epipolar constraint equation (x<sup>T</sup><sub>2</sub>*F*x<sub>1</sub> = 0) such that it
	 * is formulated as a standard linear system of the form Ax=0.  Where A contains the pixel locations and x is
	 * the reformatted fundamental matrix.
	 *
	 * @param points Set of associated points in left and right images.
	 * @param A Matrix where the reformatted points are written to.
	 */
	protected void createA(List<AssociatedPair> points, RowMatrix_F64 A ) {
		A.reshape(points.size(),9, false);
		A.zero();

		Point2D_F64 f_norm = new Point2D_F64();
		Point2D_F64 s_norm = new Point2D_F64();

		final int size = points.size();
		for( int i = 0; i < size; i++ ) {
			AssociatedPair p = points.get(i);

			Point2D_F64 f = p.p1;
			Point2D_F64 s = p.p2;

			// normalize the points
			LowLevelMultiViewOps.applyPixelNormalization(N1, f, f_norm);
			LowLevelMultiViewOps.applyPixelNormalization(N2, s, s_norm);

			// perform the Kronecker product with the two points being in
			// homogeneous coordinates (z=1)
			A.unsafe_set(i,0,s_norm.x*f_norm.x);
			A.unsafe_set(i,1,s_norm.x*f_norm.y);
			A.unsafe_set(i,2,s_norm.x);
			A.unsafe_set(i,3,s_norm.y*f_norm.x);
			A.unsafe_set(i,4,s_norm.y*f_norm.y);
			A.unsafe_set(i,5,s_norm.y);
			A.unsafe_set(i,6,f_norm.x);
			A.unsafe_set(i,7,f_norm.y);
			A.unsafe_set(i,8,1);
		}
	}

	/**
	 * Returns the U from the SVD of F.
	 *
	 * @return U matrix.
	 */
	public RowMatrix_F64 getSvdU() {
		return svdU;
	}

	/**
	 * Returns the S from the SVD of F.
	 *
	 * @return S matrix.
	 */
	public RowMatrix_F64 getSvdS() {
		return svdS;
	}

	/**
	 * Returns the V from the SVD of F.
	 *
	 * @return V matrix.
	 */
	public RowMatrix_F64 getSvdV() {
		return svdV;
	}

	/**
	 * Returns true if it is computing a fundamental matrix or false if it is an essential matrix.
	 * @return true for fundamental and false for essential
	 */
	public boolean isComputeFundamental() {
		return computeFundamental;
	}

}
