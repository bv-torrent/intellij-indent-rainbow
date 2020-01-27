package indent.rainbow

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "IndentRainbowConfig", storages = [Storage("IndentRainbowConfig.xml")])
class IrConfig : PersistentStateComponent<IrConfig> {

    var enabled: Boolean = true
    var useFormatterBasedAnnotator: Boolean = true
    var opacityMultiplier: Float = 0F  // [-1, +1]

    override fun getState(): IrConfig = this

    override fun loadState(state: IrConfig) = XmlSerializerUtil.copyBean(state, this)

    companion object {
        val instance: IrConfig
            get() = ServiceManager.getService(IrConfig::class.java)
    }
}
