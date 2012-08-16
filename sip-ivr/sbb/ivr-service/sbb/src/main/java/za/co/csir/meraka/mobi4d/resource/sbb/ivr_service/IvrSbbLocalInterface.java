package za.co.csir.meraka.mobi4d.resource.sbb.ivr_service;

/**
 * Methods declared in IvrSbbLocalInterface can be called synchronously 
 * Each of these method are implemented in SBB abstract classes
 * @author Makitla, Ishmael CSIR - Meraka
 * 2010
 *
 */
public interface IvrSbbLocalInterface extends javax.slee.SbbLocalObject {


  //TODO: Make caller and Callee to be proper SIP Addresses - or this must be done by the method implementation 
    public boolean makeSipCall(String caller, String callee);

}
