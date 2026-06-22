package vlensys.yuriasmr

import net.minecraft.resources.Identifier

object YuriAsmr {
	const val MOD_ID: String = "yuri-asmr"

	fun id(path: String): Identifier
		= Identifier.fromNamespaceAndPath(MOD_ID, path)
}
