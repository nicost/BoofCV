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

package boofcv.alg.geo.bundle.jacobians;

import boofcv.alg.geo.RodriguesRotationJacobian;
import georegression.geometry.ConvertRotation3D_F64;
import georegression.struct.so.Rodrigues_F64;
import org.ejml.data.DMatrixRMaj;

/**
 * Jacobian for 3-tuple encoded Rodrigues
 *
 * @author Peter Abeles
 */
public class JacobianSo3Rodrigues_F64 implements JacobianSo3_F64 {
	private RodriguesRotationJacobian jac = new RodriguesRotationJacobian();

	private Rodrigues_F64 rodrigues = new Rodrigues_F64();
	private DMatrixRMaj R = new DMatrixRMaj(3,3);

	@Override
	public void setParameters(double[] parameters, int offset) {
		double x = parameters[offset];
		double y = parameters[offset+1];
		double z = parameters[offset+2];

		jac.process(x,y,z);
		rodrigues.setParamVector(x,y,z);
		ConvertRotation3D_F64.rodriguesToMatrix(rodrigues,R);
	}

	@Override
	public int getParameterLength() {
		return 3;
	}

	@Override
	public DMatrixRMaj getRotationMatrix() {
		return R;
	}

	@Override
	public DMatrixRMaj getPartial(int param) {
		switch (param) {
			case 0: return jac.Rx;
			case 1: return jac.Ry;
			case 2: return jac.Rz;
		}
		throw new RuntimeException("Out of bounds parameter!");
	}

}
