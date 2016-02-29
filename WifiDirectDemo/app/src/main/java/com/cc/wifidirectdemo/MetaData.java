package com.cc.wifidirectdemo;

import android.os.Parcel;
import android.os.Parcelable;

public class MetaData implements Parcelable {
    public String absolute_path;
    public String filename;

    public MetaData() {

    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.absolute_path);
        dest.writeString(this.filename);
    }

    protected MetaData(Parcel in) {
        this.absolute_path = in.readString();
        this.filename = in.readString();
    }

    public static final Parcelable.Creator<MetaData> CREATOR = new Parcelable.Creator<MetaData>() {
        public MetaData createFromParcel(Parcel source) {
            return new MetaData(source);
        }

        public MetaData[] newArray(int size) {
            return new MetaData[size];
        }
    };
}
