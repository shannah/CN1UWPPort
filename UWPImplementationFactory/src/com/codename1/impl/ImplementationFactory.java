/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.codename1.impl;

/**
 *
 * @author steve
 */
public class ImplementationFactory {
    private static ImplementationFactory instance;
    private CodenameOneImplementation impl;
    
    /**
     * Allows third parties to replace the implementation factory
     */
    protected ImplementationFactory() {
    }
    
    /**
     * Returns the singleton instance of this class
     * 
     * @return instanceof Implementation factory
     */
    public static ImplementationFactory getInstance() {
        if (instance == null) {
            instance = new ImplementationFactory();
        }
        return instance;
    }
    
    /**
     * Install a new implementation factory this method is invoked by implementors
     * to replace a factory.
     * 
     * @param i implementation factory instance
     */
    public static void setInstance(ImplementationFactory i) {
        instance = i;
    }
    
    
    public Object createImplementation() {
        return impl;
    }
    
    public void setImplementation(CodenameOneImplementation i) {
        impl = i;
    }
}
