ImageJ の Windowのように、虫眼鏡ツールで拡大・縮小ができるSwing用の部品が欲しいと思い作成。
あくまでImageJの虫眼鏡ツールに連動しているため、ImageJプラグインの部品として使用。

I have created a mock implementation of a custom Swing component that fully synchronizes with ImageJ's Magnifying Glass tool.
This component is designed to be embedded within ImageJ plugins (such as PlugIn or PlugInFilter). 
It detects and synchronizes with the zoom level (canvas magnification) of the active image (ImagePlus), as well as user click events triggered by the magnifier tool.
