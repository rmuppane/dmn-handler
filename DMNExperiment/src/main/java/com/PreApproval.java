package com;

/**
 * This class was automatically generated by the data modeler tool.
 */

public class PreApproval implements java.io.Serializable {

	static final long serialVersionUID = 1L;

	private java.util.List<java.lang.String> reasons;
	private java.lang.Boolean approved;

	public PreApproval() {
	}

	public java.util.List<java.lang.String> getReasons() {
		return this.reasons;
	}

	public void setReasons(java.util.List<java.lang.String> reasons) {
		this.reasons = reasons;
	}

	public java.lang.Boolean getApproved() {
		return this.approved;
	}

	public void setApproved(java.lang.Boolean approved) {
		this.approved = approved;
	}

	public PreApproval(java.util.List<java.lang.String> reasons,
			java.lang.Boolean approved) {
		this.reasons = reasons;
		this.approved = approved;
	}

}