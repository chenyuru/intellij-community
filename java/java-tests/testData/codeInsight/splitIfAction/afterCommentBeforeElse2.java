class C {
    void foo() {
        if (a) {
            if (b) {
                call();
            } // foo
            else if (c) {
                otherCall();
            } // bar
            else {
                thirdCall();
            } // baz
        } else {
            fourthCall();
        } // qux
    }
}