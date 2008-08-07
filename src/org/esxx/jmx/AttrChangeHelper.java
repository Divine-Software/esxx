
package org.esxx.jmx;

import javax.management.AttributeChangeNotification;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanNotificationInfo;
import javax.management.ReflectionException;
import javax.management.StandardEmitterMBean;

/**
 * A helper class used to prepare, then emit, a new AttributeChangeNotification
 * This class deals with any mxbean translations
 */
public class AttrChangeHelper {
    
    final StandardEmitterMBean mbean;
        
    long seqNo = 0;
    
    /**
     * Creates a new instance of AttrChangeHelper
     */
    public AttrChangeHelper(
            StandardEmitterMBean mbean) {
        
        this.mbean = mbean;
    }
 
    public static MBeanNotificationInfo[] getNotificationInfo() {
        return new MBeanNotificationInfo[] {
            new MBeanNotificationInfo(
                new String[] { AttributeChangeNotification.ATTRIBUTE_CHANGE },
                "javax.management.AttributeChangeNotification", 
                "Attribute change notification")
        };
    }
        
    public synchronized Change newChange(String attributeName) {
        try {

            // for mxbeans - this will translate into the
            // open type available on our MBean. For normal
            // MBeans, this will just get the normal type.
            
            return new Change(attributeName, mbean.getAttribute(attributeName));
        } catch (AttributeNotFoundException ex) {
            throw new IllegalArgumentException("Attribute not found : "+attributeName, ex);
        } catch (ReflectionException ex) {
            throw new IllegalArgumentException("Attribute not found : "+attributeName, ex);
        } catch (MBeanException ex) {
            throw new IllegalArgumentException("Attribute not found : "+attributeName, ex);
        }
        
    }
    
    public class Change {

        String attributeName;
        Object oldValue;
        
        private Change(String attributeName, Object oldValue) {
            
            this.attributeName = attributeName;
            this.oldValue = oldValue;
        }
        
        public synchronized void end() {
            
            Object newValue = null;
            try {

                // for mxbeans - this will translate into the
                // open type available on our MBean. For normal
                // MBeans, this will just get the normal type.

                newValue = mbean.getAttribute(attributeName);
            } catch (AttributeNotFoundException ex) {
                throw new IllegalArgumentException("Attribute not found : "+attributeName, ex);
            } catch (ReflectionException ex) {
                throw new IllegalArgumentException("Attribute not found : "+attributeName, ex);
            } catch (MBeanException ex) {
                throw new IllegalArgumentException("Attribute not found : "+attributeName, ex);
            }

            AttributeChangeNotification notif = new AttributeChangeNotification(
                    mbean,
                    seqNo++,
                    System.currentTimeMillis(),
                    "Attribute \"" + attributeName + "\" changed",
                    attributeName,
                    newValue.getClass().getName(),
                    oldValue,
                    newValue);
            mbean.sendNotification(notif);
        }
    }
    
}
