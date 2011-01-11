/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.renderscript;

import java.io.IOException;
import java.io.InputStream;

import android.content.res.Resources;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.TypedValue;

/**
 * Memory allocation class for renderscript.  An allocation combines a Type with
 * memory to provide storage for user data and objects.
 *
 * Allocations may exist in one or more memory spaces.  Currently those are
 * Script: accessable by RS scripts.
 * Graphics Texture: accessable as a graphics texture.
 * Graphics Vertex: accessable as graphical vertex data.
 * Graphics Constants: Accessable as constants in user shaders
 *
 * By default java side updates are always applied to the script accessable
 * memory.  If this is not present they are then applied to the various HW
 * memory types.  A syncAll call is necessary after the script data is update to
 * keep the other memory spaces in sync.
 *
 **/
public class Allocation extends BaseObj {
    Type mType;
    Bitmap mBitmap;
    int mUsage;

    public static final int USAGE_SCRIPT = 0x0001;
    public static final int USAGE_GRAPHICS_TEXTURE = 0x0002;
    public static final int USAGE_GRAPHICS_VERTEX = 0x0004;
    public static final int USAGE_GRAPHICS_CONSTANTS = 0x0008;


    public enum MipmapControl {
        MIPMAP_NONE(0),
        MIPMAP_FULL(1),
        MIPMAP_ON_SYNC_TO_TEXTURE(2);

        int mID;
        MipmapControl(int id) {
            mID = id;
        }
    }

    Allocation(int id, RenderScript rs, Type t, int usage) {
        super(id, rs);
        if ((usage & ~(USAGE_SCRIPT |
                       USAGE_GRAPHICS_TEXTURE |
                       USAGE_GRAPHICS_VERTEX |
                       USAGE_GRAPHICS_CONSTANTS)) != 0) {
            throw new RSIllegalArgumentException("Unknown usage specified.");
        }
        mType = t;
    }

    @Override
    void updateFromNative() {
        super.updateFromNative();
        int typeID = mRS.nAllocationGetType(getID());
        if(typeID != 0) {
            mType = new Type(typeID, mRS);
            mType.updateFromNative();
        }
    }

    public Type getType() {
        return mType;
    }

    public void syncAll(int srcLocation) {
        switch (srcLocation) {
        case USAGE_SCRIPT:
        case USAGE_GRAPHICS_CONSTANTS:
        case USAGE_GRAPHICS_TEXTURE:
        case USAGE_GRAPHICS_VERTEX:
            break;
        default:
            throw new RSIllegalArgumentException("Source must be exactly one usage type.");
        }
        mRS.validate();
        mRS.nAllocationSyncAll(getID(), srcLocation);
    }

    public void copyFrom(BaseObj[] d) {
        mRS.validate();
        if (d.length != mType.getCount()) {
            throw new RSIllegalArgumentException("Array size mismatch, allocation sizeX = " +
                                                 mType.getCount() + ", array length = " + d.length);
        }
        int i[] = new int[d.length];
        for (int ct=0; ct < d.length; ct++) {
            i[ct] = d[ct].getID();
        }
        copy1DRangeFrom(0, mType.getCount(), i);
    }

    private void validateBitmap(Bitmap b) {
        mRS.validate();
        if(mType.getX() != b.getWidth() ||
           mType.getY() != b.getHeight()) {
            throw new RSIllegalArgumentException("Cannot update allocation from bitmap, sizes mismatch");
        }
    }

    public void copyFrom(int[] d) {
        mRS.validate();
        copy1DRangeFrom(0, mType.getCount(), d);
    }
    public void copyFrom(short[] d) {
        mRS.validate();
        copy1DRangeFrom(0, mType.getCount(), d);
    }
    public void copyFrom(byte[] d) {
        mRS.validate();
        copy1DRangeFrom(0, mType.getCount(), d);
    }
    public void copyFrom(float[] d) {
        mRS.validate();
        copy1DRangeFrom(0, mType.getCount(), d);
    }
    public void copyFrom(Bitmap b) {
        validateBitmap(b);
        mRS.nAllocationCopyFromBitmap(getID(), b);
    }

    /**
     * @hide
     *
     * This is only intended to be used by auto-generate code reflected from the
     * renderscript script files.
     *
     * @param xoff
     * @param fp
     */
    public void setOneElement(int xoff, FieldPacker fp) {
        int eSize = mType.mElement.getSizeBytes();
        final byte[] data = fp.getData();

        int count = data.length / eSize;
        if ((eSize * count) != data.length) {
            throw new RSIllegalArgumentException("Field packer length " + data.length +
                                               " not divisible by element size " + eSize + ".");
        }
        data1DChecks(xoff, count, data.length, data.length);
        mRS.nAllocationData1D(getID(), xoff, 0, count, data, data.length);
    }


    /**
     * @hide
     *
     * This is only intended to be used by auto-generate code reflected from the
     * renderscript script files.
     *
     * @param xoff
     * @param component_number
     * @param fp
     */
    public void setOneComponent(int xoff, int component_number, FieldPacker fp) {
        if (component_number >= mType.mElement.mElements.length) {
            throw new RSIllegalArgumentException("Component_number " + component_number + " out of range.");
        }
        if(xoff < 0) {
            throw new RSIllegalArgumentException("Offset must be >= 0.");
        }

        final byte[] data = fp.getData();
        int eSize = mType.mElement.mElements[component_number].getSizeBytes();

        if (data.length != eSize) {
            throw new RSIllegalArgumentException("Field packer sizelength " + data.length +
                                               " does not match component size " + eSize + ".");
        }

        mRS.nAllocationElementData1D(getID(), xoff, 0, component_number, data, data.length);
    }

    private void data1DChecks(int off, int count, int len, int dataSize) {
        mRS.validate();
        if(off < 0) {
            throw new RSIllegalArgumentException("Offset must be >= 0.");
        }
        if(count < 1) {
            throw new RSIllegalArgumentException("Count must be >= 1.");
        }
        if((off + count) > mType.getCount()) {
            throw new RSIllegalArgumentException("Overflow, Available count " + mType.getCount() +
                                               ", got " + count + " at offset " + off + ".");
        }
        if((len) < dataSize) {
            throw new RSIllegalArgumentException("Array too small for allocation type.");
        }
    }

    public void copy1DRangeFrom(int off, int count, int[] d) {
        int dataSize = mType.mElement.getSizeBytes() * count;
        data1DChecks(off, count, d.length * 4, dataSize);
        mRS.nAllocationData1D(getID(), off, 0, count, d, dataSize);
    }
    public void copy1DRangeFrom(int off, int count, short[] d) {
        int dataSize = mType.mElement.getSizeBytes() * count;
        data1DChecks(off, count, d.length * 2, dataSize);
        mRS.nAllocationData1D(getID(), off, 0, count, d, dataSize);
    }
    public void copy1DRangeFrom(int off, int count, byte[] d) {
        int dataSize = mType.mElement.getSizeBytes() * count;
        data1DChecks(off, count, d.length, dataSize);
        mRS.nAllocationData1D(getID(), off, 0, count, d, dataSize);
    }
    public void copy1DRangeFrom(int off, int count, float[] d) {
        int dataSize = mType.mElement.getSizeBytes() * count;
        data1DChecks(off, count, d.length * 4, dataSize);
        mRS.nAllocationData1D(getID(), off, 0, count, d, dataSize);
    }


    public void copy2DRangeFrom(int xoff, int yoff, int w, int h, byte[] d) {
        mRS.validate();
        mRS.nAllocationData2D(getID(), xoff, yoff, 0, 0, w, h, d, d.length);
    }

    public void copy2DRangeFrom(int xoff, int yoff, int w, int h, short[] d) {
        mRS.validate();
        mRS.nAllocationData2D(getID(), xoff, yoff, 0, 0, w, h, d, d.length * 2);
    }

    public void copy2DRangeFrom(int xoff, int yoff, int w, int h, int[] d) {
        mRS.validate();
        mRS.nAllocationData2D(getID(), xoff, yoff, 0, 0, w, h, d, d.length * 4);
    }

    public void copy2DRangeFrom(int xoff, int yoff, int w, int h, float[] d) {
        mRS.validate();
        mRS.nAllocationData2D(getID(), xoff, yoff, 0, 0, w, h, d, d.length * 4);
    }

    public void copy2DRangeFrom(int xoff, int yoff, Bitmap b) {
        mRS.validate();
        mRS.nAllocationData2D(getID(), xoff, yoff, 0, 0, b);
    }


    public void copyTo(Bitmap b) {
        validateBitmap(b);
        mRS.nAllocationCopyToBitmap(getID(), b);
    }

    public void copyTo(byte[] d) {
        mRS.validate();
        mRS.nAllocationRead(getID(), d);
    }

    public void copyTo(short[] d) {
        mRS.validate();
        mRS.nAllocationRead(getID(), d);
    }

    public void copyTo(int[] d) {
        mRS.validate();
        mRS.nAllocationRead(getID(), d);
    }

    public void copyTo(float[] d) {
        mRS.validate();
        mRS.nAllocationRead(getID(), d);
    }

    public synchronized void resize(int dimX) {
        if ((mType.getY() > 0)|| (mType.getZ() > 0) || mType.hasFaces() || mType.hasMipmaps()) {
            throw new RSInvalidStateException("Resize only support for 1D allocations at this time.");
        }
        mRS.nAllocationResize1D(getID(), dimX);
        mRS.finish();  // Necessary because resize is fifoed and update is async.

        int typeID = mRS.nAllocationGetType(getID());
        mType = new Type(typeID, mRS);
        mType.updateFromNative();
    }

    /*
    public void resize(int dimX, int dimY) {
        if ((mType.getZ() > 0) || mType.getFaces() || mType.getLOD()) {
            throw new RSIllegalStateException("Resize only support for 2D allocations at this time.");
        }
        if (mType.getY() == 0) {
            throw new RSIllegalStateException("Resize only support for 2D allocations at this time.");
        }
        mRS.nAllocationResize2D(getID(), dimX, dimY);
    }
    */



    // creation

    static BitmapFactory.Options mBitmapOptions = new BitmapFactory.Options();
    static {
        mBitmapOptions.inScaled = false;
    }

    static public Allocation createTyped(RenderScript rs, Type type, MipmapControl mc, int usage) {
        rs.validate();
        if (type.getID() == 0) {
            throw new RSInvalidStateException("Bad Type");
        }
        int id = rs.nAllocationCreateTyped(type.getID(), mc.mID, usage);
        if (id == 0) {
            throw new RSRuntimeException("Allocation creation failed.");
        }
        return new Allocation(id, rs, type, usage);
    }

    static public Allocation createTyped(RenderScript rs, Type type, int usage) {
        return createTyped(rs, type, MipmapControl.MIPMAP_NONE, usage);
    }

    static public Allocation createTyped(RenderScript rs, Type type) {
        return createTyped(rs, type, MipmapControl.MIPMAP_NONE, USAGE_SCRIPT);
    }

    static public Allocation createSized(RenderScript rs, Element e,
                                         int count, int usage) {
        rs.validate();
        Type.Builder b = new Type.Builder(rs, e);
        b.setX(count);
        Type t = b.create();

        int id = rs.nAllocationCreateTyped(t.getID(), MipmapControl.MIPMAP_NONE.mID, usage);
        if (id == 0) {
            throw new RSRuntimeException("Allocation creation failed.");
        }
        return new Allocation(id, rs, t, usage);
    }

    static public Allocation createSized(RenderScript rs, Element e, int count) {
        return createSized(rs, e, count, USAGE_SCRIPT);
    }

    static Element elementFromBitmap(RenderScript rs, Bitmap b) {
        final Bitmap.Config bc = b.getConfig();
        if (bc == Bitmap.Config.ALPHA_8) {
            return Element.A_8(rs);
        }
        if (bc == Bitmap.Config.ARGB_4444) {
            return Element.RGBA_4444(rs);
        }
        if (bc == Bitmap.Config.ARGB_8888) {
            return Element.RGBA_8888(rs);
        }
        if (bc == Bitmap.Config.RGB_565) {
            return Element.RGB_565(rs);
        }
        throw new RSInvalidStateException("Bad bitmap type: " + bc);
    }

    static Type typeFromBitmap(RenderScript rs, Bitmap b,
                                       MipmapControl mip) {
        Element e = elementFromBitmap(rs, b);
        Type.Builder tb = new Type.Builder(rs, e);
        tb.setX(b.getWidth());
        tb.setY(b.getHeight());
        tb.setMipmaps(mip == MipmapControl.MIPMAP_FULL);
        return tb.create();
    }

    static public Allocation createFromBitmap(RenderScript rs, Bitmap b,
                                              MipmapControl mips,
                                              int usage) {
        rs.validate();
        Type t = typeFromBitmap(rs, b, mips);

        int id = rs.nAllocationCreateFromBitmap(t.getID(), mips.mID, b, usage);
        if (id == 0) {
            throw new RSRuntimeException("Load failed.");
        }
        return new Allocation(id, rs, t, usage);
    }

    static public Allocation createFromBitmap(RenderScript rs, Bitmap b) {
        return createFromBitmap(rs, b, MipmapControl.MIPMAP_NONE,
                                USAGE_GRAPHICS_TEXTURE);
    }

    /**
    * Creates a cubemap allocation from a bitmap containing the
    * horizontal list of cube faces. Each individual face must be
    * the same size and power of 2
    *
    * @param rs
    * @param b bitmap with cubemap faces layed out in the following
    *          format: right, left, top, bottom, front, back
    * @param mips specifies desired mipmap behaviour for the cubemap
    * @param usage bitfield specifying how the cubemap is utilized
    *
    **/
    static public Allocation createCubemapFromBitmap(RenderScript rs, Bitmap b,
                                                     MipmapControl mips,
                                                     int usage) {
        rs.validate();

        int height = b.getHeight();
        int width = b.getWidth();

        if (width % 6 != 0) {
            throw new RSIllegalArgumentException("Cubemap height must be multiple of 6");
        }
        if (width / 6 != height) {
            throw new RSIllegalArgumentException("Only square cobe map faces supported");
        }
        boolean isPow2 = (height & (height - 1)) == 0;
        if (!isPow2) {
            throw new RSIllegalArgumentException("Only power of 2 cube faces supported");
        }

        Element e = elementFromBitmap(rs, b);
        Type.Builder tb = new Type.Builder(rs, e);
        tb.setX(height);
        tb.setY(height);
        tb.setFaces(true);
        tb.setMipmaps(mips == MipmapControl.MIPMAP_FULL);
        Type t = tb.create();

        int id = rs.nAllocationCubeCreateFromBitmap(t.getID(), mips.mID, b, usage);
        if(id == 0) {
            throw new RSRuntimeException("Load failed for bitmap " + b + " element " + e);
        }
        return new Allocation(id, rs, t, usage);
    }

    static public Allocation createCubemapFromBitmap(RenderScript rs, Bitmap b) {
        return createCubemapFromBitmap(rs, b, MipmapControl.MIPMAP_NONE,
                                       USAGE_GRAPHICS_TEXTURE);
    }

    static public Allocation createFromBitmapResource(RenderScript rs,
                                                      Resources res,
                                                      int id,
                                                      MipmapControl mips,
                                                      int usage) {

        rs.validate();
        Bitmap b = BitmapFactory.decodeResource(res, id);
        Allocation alloc = createFromBitmap(rs, b, mips, usage);
        b.recycle();
        return alloc;
    }

    static public Allocation createFromBitmapResource(RenderScript rs,
                                                      Resources res,
                                                      int id) {
        return createFromBitmapResource(rs, res, id,
                                        MipmapControl.MIPMAP_NONE,
                                        USAGE_GRAPHICS_TEXTURE);
    }

    static public Allocation createFromString(RenderScript rs,
                                              String str,
                                              int usage) {
        rs.validate();
        byte[] allocArray = null;
        try {
            allocArray = str.getBytes("UTF-8");
            Allocation alloc = Allocation.createSized(rs, Element.U8(rs), allocArray.length, usage);
            alloc.copyFrom(allocArray);
            return alloc;
        }
        catch (Exception e) {
            throw new RSRuntimeException("Could not convert string to utf-8.");
        }
    }
}


