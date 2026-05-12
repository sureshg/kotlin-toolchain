import compose_resources_custom_res_class.generated.resources.MyResources
import compose_resources_custom_res_class.generated.resources.compose

fun main() {
    // Verify that the custom Res class name is generated
    MyResources.drawable.compose
    println(MyResources::class.simpleName)
}
