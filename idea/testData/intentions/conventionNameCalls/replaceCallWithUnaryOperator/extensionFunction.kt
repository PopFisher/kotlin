fun test() {
    class Test()

    operator fun Test.unaryPlus(): Test = Test()

    val test = Test()
    test.unaryPl<caret>us()
}
